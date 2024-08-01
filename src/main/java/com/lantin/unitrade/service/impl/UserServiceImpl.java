package com.lantin.unitrade.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lantin.unitrade.constant.SystemConstants;
import com.lantin.unitrade.domain.dto.LoginFormDTO;
import com.lantin.unitrade.domain.dto.Result;
import com.lantin.unitrade.domain.dto.UserDTO;
import com.lantin.unitrade.domain.po.User;
import com.lantin.unitrade.domain.po.UserInfo;
import com.lantin.unitrade.enums.UserStatus;
import com.lantin.unitrade.exception.BizIllegalException;
import com.lantin.unitrade.mapper.UserMapper;
import com.lantin.unitrade.service.IUserService;
import com.lantin.unitrade.utils.PasswordEncoder;
import com.lantin.unitrade.utils.RegexUtils;
import com.lantin.unitrade.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.lantin.unitrade.constant.RedisConstants.*;
import static com.lantin.unitrade.constant.SystemConstants.PASSWORD_SALT;


@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);

        // 2. 如果不符合，返回错误信息
        if (phoneInvalid) {
            return Result.fail("手机号格式错误！");
        }

        // 3. 如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4. 保存验证码到redis
        // 注意要设置验证码的有效期 set key value ex 120
        // 手机号作为key
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5. 发送验证码
        // 如果真实发送需要使用第三方短信服务，这里采用模拟发送
        log.info("发送短信验证码成功，验证码：{}", code);

        // 返回ok
        // return Result.ok(code); // TODO 生成完token后改回下面
        return Result.ok();
    }


    /**
     * 短信验证码登录/注册
     * 如果验证码正确且该手机号尚未注册，则直接快捷注册并完成登录
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();

        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        // 2. 从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 3. 如果验证码不一致则报错
            return Result.fail("验证码错误！");
        }

        // 4. 如果验证码一致，则根据手机号查询用户
        // 用mybatis-plus省去写sql语句
        User user = query().eq("phone", phone).one();

        // 5. 判断用户是否存在
        if (user == null) {
            // 6. 如果用户不存在，则创建新用户并保存
            user = createUserWithPhone(phone);
        }

        // 7. 保存用户信息到redis中
        // 7.1 生成随机token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2 将User对象转为HashMap存储
        // 不要将所有用户信息放到session，这样并不安全
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));    // 这里得将HashMap中的字段值转成字符串
        // 7.3 存储到redis
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4 设置token的有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 返回token
        return Result.ok(token);
    }


    /**
     * 根据手机号创建用户
     *
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        // 1. 创建用户
        User user = new User();
        user.setPhone(phone)
                .setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10))
                .setStatus(UserStatus.NORMAL.getValue())
                .setBalance(10000); // 测试就直接给10000元

        // 2. 保存用户到数据库
        save(user);
        return user;
    }


    /**
     * 用户签到
     * bitmap实现
     *
     * @return
     */
    @Override
    public Result sign() {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        // 2. 获取日期
        LocalDate now = LocalDate.now();

        // 3. 拼接key 如 sign:1:202405
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;

        // 4. 获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();   // 从1到31

        // 5. 写入redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);   // bitmap中第n个bit的offset为n-1

        return Result.ok();
    }


    /**
     * 统计当前用户截至当前时间在本月的连续签到天数
     *
     * @return
     */
    @Override
    public Result signCount() {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        // 2. 获取日期
        LocalDate now = LocalDate.now();

        // 3. 拼接key 如 sign:1:202405
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;

        // 4. 获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();   // 从1到31

        // 5. 从redis中查出当前用户在本月至今的所有签到记录（返回的是一个十进制数） BITFIELD sign:1:202405 GET u14 0
        List<Long> results = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));

        // 为什么返回的是一个List？——因为BITFIELD可以同时执行多个子命令，就会有多个结果
        if (results == null || results.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }

        Long num = results.get(0);  // 明确知道只执行了一个GET子命令，所以直接取第一个结果即可
        if (num == null || num == 0) {
            // 没有任何签到结果
            return Result.ok(0);
        }

        // 6. 从后向前循环检查每一bit位
        int count = 0;
        while (true) {
            // 让这个数字与1做与运算，得到数字的最后一个bit位并判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            } else {
                // 如果不为0，说明已签到，计数器加1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续向前检查下一个bit位
            num >>>= 1; // 逻辑右移1位
        }

        return Result.ok(count);
    }

    /**
     * 查询用户个人信息
     * 当查看他人用户主页时用到，不包含隐私信息
     * @param userId
     * @return
     */
    @Override
    public Result getUserInfo(Long userId) {
        // 查询详情
        User user = getById(userId);
        if (user == null) {
            return Result.ok();
        }
        // 只将对应信息封装到 UserInfo 中返回
        UserInfo info = BeanUtil.copyProperties(user, UserInfo.class);
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 用户支付后扣减用户余额
     * @param pw 用户支付时输入的密码
     * @param totalFee
     */
    @Override
    @Transactional
    public void deductMoney(String pw, Integer totalFee) {
        log.info("开始扣款");
        // 1.校验密码
        Long userId = UserHolder.getUser().getId();
        User user = getById(userId);
        if(user == null || !PasswordEncoder.matches(user.getPassword(), pw)){   // 比较数据库中的加密密码和用户输入的密码是否一致
            // 密码错误
            throw new BizIllegalException("用户密码错误");
        }

        // 2.尝试扣款
        try {
            baseMapper.updateMoney(userId, totalFee);
        } catch (Exception e) {
            throw new RuntimeException("扣款失败，可能是余额不足！", e);
        }
        log.info("扣款成功");
    }

    /**
     * 更新用户信息
     * @param user
     */
    @Override
    public void updateUser(User user) {
        // 如果要更新的数据中包含密码，则需要加密再存储到数据库
        if (StrUtil.isNotBlank(user.getPassword())) {
            PasswordEncoder.encode(user.getPassword(), PASSWORD_SALT);
        }
        updateById(user);
    }
}
