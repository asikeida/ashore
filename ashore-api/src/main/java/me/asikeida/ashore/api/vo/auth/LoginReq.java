package me.asikeida.ashore.api.vo.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求：账号（用户名或邮箱）+ 密码。
 */
public record LoginReq(

        @NotBlank(message = "账号不能为空")
        String account,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
