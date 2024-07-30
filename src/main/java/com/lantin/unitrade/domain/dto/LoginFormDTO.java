package com.lantin.unitrade.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
@ApiModel(description = "登录表单实体")
public class LoginFormDTO {
    @ApiModelProperty(value = "手机号", required = true)
    @NotNull(message = "手机号不能为空")
    private String phone;
    @ApiModelProperty(value = "验证码")
    @NotNull(message = "验证码不能为空")
    private String code;
    @ApiModelProperty(value = "密码")
    private String password;
}
