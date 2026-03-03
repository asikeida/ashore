package me.asikeida.ashore.web.admin;

import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.core.permission.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅测试用：受 ADMIN 级别保护的端点，用于拦截器角色判定的 403/200 断言。
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
}
