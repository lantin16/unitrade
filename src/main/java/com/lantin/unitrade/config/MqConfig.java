package com.lantin.unitrade.config;

import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantin.unitrade.domain.dto.UserDTO;
import com.lantin.unitrade.utils.RabbitMqHelper;
import com.lantin.unitrade.utils.UserHolder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mq的配置类
 */

@Configuration
@ConditionalOnClass(value = {MessageConverter.class, RabbitTemplate.class})   // 只有当服务有MessageConverter和RabbitTemplate类时此配置才生效，防止没有引入mq依赖的服务启动时报错
public class MqConfig {

    @Bean
    @ConditionalOnBean(ObjectMapper.class)  // 只有在 Spring 容器中存在 ObjectMapper Bean 时，messageConverter 方法才会被调用并创建 Jackson2JsonMessageConverter Bean。
    public MessageConverter messageConverter() {
        // 定义消息转换器，用于将消息转换为json格式，否则会用JDK默认的序列化方式
        Jackson2JsonMessageConverter jjmc = new Jackson2JsonMessageConverter() {
            @Override
            public Object fromMessage(Message message) throws MessageConversionException {
                // 消费者获取消息体中的登录用户来处理业务，如何更优雅地传输登录用户信息，让使用MQ的人无感知，依然采用UserHolder来随时获取用户？
                // 通过消息头传递用户信息，消息头是一个Map，可以存放任意的键值对，这里存放用户id
                // 为了避免在每个Listener都加同样的setUserId的方法，考虑在消息转换器里加。因为无论哪个listener接受，都需要经过消息转换器，因此重写消息转化器的fromMessage方法。
                UserDTO userDTO = message.getMessageProperties().getHeader("user-info");
                if(ObjectUtil.isNotNull(userDTO)) {
                    UserHolder.saveUser(userDTO);   // 从消息头中取出用户信息并保存到ThreadLocal
                }
                return super.fromMessage(message);
            }
        };
        // 配置自动创建消息id，用于识别不同的消息
        jjmc.setCreateMessageIds(true);
        return jjmc;
    }

    /**
     * 消息后处理器，用于在发送消息时添加用户信息到消息头
     * @param
     * @return
     */
    // @Bean
    // public MessagePostProcessor userInfoPostProcessor(Long userId) {
    //     return message -> {
    //         UserDTO user = UserHolder.getUser();
    //         if (user != null) {
    //             message.getMessageProperties().setHeader("user-info", user);
    //         }
    //         return message;
    //     };
    // }


    @Bean   // 将RabbitMqHelper注册为Bean
    public RabbitMqHelper rabbitMqHelper(RabbitTemplate rabbitTemplate) {
        return new RabbitMqHelper(rabbitTemplate);
    }

}
