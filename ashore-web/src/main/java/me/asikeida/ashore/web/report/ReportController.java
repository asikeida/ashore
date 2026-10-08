package me.asikeida.ashore.web.report;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.context.ReqContext;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.report.ReportItemVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.core.permission.UserRole;
import me.asikeida.ashore.service.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报端点：用户提交（文章/评论），管理台处理队列。
 */
@Tag(name = "举报", description = "违规内容举报与管理台处理")
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 提交举报请求体 */
    public record ReportReq(
            @NotBlank(message = "举报目标类型不能为空") String targetType,
            @NotBlank(message = "举报目标不能为空") String targetId,
            @NotBlank(message = "举报理由不能为空") @Size(max = 500, message = "理由不超过 500 字") String reason) {
    }

    @Operation(summary = "提交举报", description = "需登录；targetType=ARTICLE/COMMENT；同人对同一目标的待处理举报去重")
    @Permission
    @PostMapping("/reports")
    public ResVo<Void> submit(@Valid @RequestBody ReportReq req) {
        LoginUser user = ReqContext.loginUser();
        reportService.submit(user, req.targetType(), Long.valueOf(req.targetId()), req.reason());
        return ResVo.ok();
    }

    @Operation(summary = "举报处理队列", description = "仅管理员；status 可选（PENDING/RESOLVED/DISMISSED）")
    @Permission(UserRole.ADMIN)
    @GetMapping("/admin/reports")
    public ResVo<List<ReportItemVo>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") Integer limit) {
        return ResVo.ok(reportService.adminList(status, limit));
    }

    /** 处理请求体：action = DISMISS（驳回）/ OFFLINE（处置内容） */
    public record HandleReq(@NotBlank String action) {
    }

    @Operation(summary = "处理举报", description = "仅管理员；action=DISMISS 驳回 / OFFLINE 处置内容（文章下线或评论软删）")
    @Permission(UserRole.ADMIN)
    @PostMapping("/admin/reports/{id}/handle")
    public ResVo<Void> handle(@PathVariable Long id, @Valid @RequestBody HandleReq req) {
        LoginUser admin = ReqContext.loginUser();
        reportService.handle(id, req.action(), admin);
        return ResVo.ok();
    }
}
