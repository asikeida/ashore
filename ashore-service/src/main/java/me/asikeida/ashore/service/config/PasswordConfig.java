package me.asikeida.ashore.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器：BCrypt（自带随机盐、慢哈希抗爆破）。
 * 仅引入 spring-security-crypto 单一模块，不引入 Spring Security 全家桶；
 * 相比 paicoding 的 MD5+盐方案有意升级，强度因子默认 10（单次约 100ms，登录场景可接受）。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
