package me.asikeida.ashore.web.user;

import jakarta.validation.Valid;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.auth.LoginReq;
import me.asikeida.ashore.api.vo.auth.RegisterReq;
import me.asikeida.ashore.api.vo.auth.UserVo;
import me.asikeida.ashore.service.user.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账密注册与登录入口。M2-3 接入会话后，登录成功将在此种发 ashore_session Cookie。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResVo<UserVo> register(@Valid @RequestBody RegisterReq req) {
        return ResVo.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ResVo<UserVo> login(@Valid @RequestBody LoginReq req) {
        return ResVo.ok(authService.login(req));
    }
}
