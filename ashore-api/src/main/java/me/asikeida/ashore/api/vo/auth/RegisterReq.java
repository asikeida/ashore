package me.asikeida.ashore.api.vo.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。record 组件上的约束注解会传播到字段，配合 @Valid 生效。
 */
public record RegisterReq(

        /** 登录账号：用户名或邮箱，全局唯一 */
        @NotBlank(message = "账号不能为空")
        @Size(min = 4, max = 32, message = "账号长度需 4-32 位")
        @Pattern(regexp = "^[A-Za-z0-9_@.]+$", message = "账号仅支持字母、数字及 _ @ . 字符")
        String account,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需 6-64 位")
        String password,

        /** 可选；缺省时由服务端生成默认昵称 */
        @Size(max = 24, message = "昵称最长 24 字")
        String nickname
) {
}
