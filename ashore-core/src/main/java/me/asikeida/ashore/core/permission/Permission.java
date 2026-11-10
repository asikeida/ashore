package me.asikeida.ashore.core.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限声明，标注在 Controller 方法或类上（默认 {@link UserRole#LOGIN}）。
 * <p>未标注的端点视为公开；执行者是 MVC 拦截器（web 模块 AuthInterceptor）而非 AOP——
 * 需要在进入 Controller 前拦截并区分响应形态。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Permission {

    UserRole value() default UserRole.LOGIN;
}
