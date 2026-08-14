package me.asikeida.ashore.web.user;

import java.util.List;
import me.asikeida.ashore.api.context.ReqContext;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.auth.UserVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.core.permission.UserRole;
import me.asikeida.ashore.service.user.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理台用户端点（仅 ADMIN）：用户列表 / 封禁 / 解封。
 * 封禁的会话强下线在 UserAdminService 内配对完成，Controller 不感知吊销细节。
 */
@Tag(name = "管理台用户", description = "管理员：用户列表、封禁（强下线）、解封")
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @Operation(summary = "用户管理列表", description = "仅管理员；accountKeyword 可选模糊匹配，create_time 倒序")
    @Permission(UserRole.ADMIN)
    @GetMapping
    public ResVo<List<UserVo>> list(
            @RequestParam(required = false) String accountKeyword,
            @RequestParam(defaultValue = "50") Integer limit) {
        return ResVo.ok(userAdminService.list(accountKeyword, limit));
    }

    @Operation(summary = "封禁用户", description = "仅管理员；status→BANNED 并吊销全部存量会话（强下线）；幂等；禁止封禁 ADMIN/自己")
    @Permission(UserRole.ADMIN)
    @PostMapping("/{userId}/ban")
    public ResVo<UserVo> ban(@PathVariable Long userId) {
        return ResVo.ok(userAdminService.ban(userId, ReqContext.loginUser().userId()));
    }

    @Operation(summary = "解封用户", description = "仅管理员；status→ACTIVE（存量会话已在封禁时清空）；幂等")
    @Permission(UserRole.ADMIN)
    @PostMapping("/{userId}/unban")
    public ResVo<UserVo> unban(@PathVariable Long userId) {
        return ResVo.ok(userAdminService.unban(userId));
    }
}
