package me.asikeida.ashore.web.admin;

import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.core.permission.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅测试用：受 ADMIN 级别保护的端点与公开端点，用于拦截器判定与身份解析的断言。
 * 位于测试源目录，不会进入生产构件；被组件扫描后所有 web 测试上下文均可见（无副作用）。
 */
@RestController
@RequestMapping("/api/test")
public class TestAdminController {

    @Permission(UserRole.ADMIN)
    @GetMapping("/admin-only")
    public ResVo<String> adminOnly() {
        return ResVo.ok("admin-ok");
    }

    /** 公开端点（无 @Permission）：回显拦截器解析出的身份——公开端点身份解析修复的验证口 */
    @GetMapping("/whoami")
    public ResVo<String> whoami() {
        me.asikeida.ashore.api.context.LoginUser user = me.asikeida.ashore.api.context.ReqContext.loginUser();
        return ResVo.ok(user == null ? "anonymous" : user.role() + ":" + user.account());
    }
}
