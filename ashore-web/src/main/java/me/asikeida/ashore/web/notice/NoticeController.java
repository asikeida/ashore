package me.asikeida.ashore.web.notice;

import java.util.List;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.context.ReqContext;
import me.asikeida.ashore.api.enums.NotifyTypeEnum;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.notice.NoticeItemVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.service.interact.service.NotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知端点：列表（类型筛选）、未读数、全部已读。仅操作当前登录用户自己的通知。
 */
@Tag(name = "通知", description = "站内通知的查询与已读")
@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final NotifyService notifyService;

    public NoticeController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @Operation(summary = "通知列表", description = "需登录；新到旧；type 可选（COMMENT/REPLY/PRAISE/COLLECT/SYSTEM）")
    @Permission
    @GetMapping
    public ResVo<List<NoticeItemVo>> list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        NotifyTypeEnum notifyType = type == null || type.isBlank() ? null : NotifyTypeEnum.valueOf(type);
        return ResVo.ok(notifyService.list(current().userId(), notifyType, page, pageSize));
    }

    @Operation(summary = "未读数", description = "需登录")
    @Permission
    @GetMapping("/unread-count")
    public ResVo<Long> unreadCount() {
        return ResVo.ok(notifyService.unreadCount(current().userId()));
    }

    @Operation(summary = "全部已读", description = "需登录；将当前用户全部未读标记为已读")
    @Permission
    @PutMapping("/read")
    public ResVo<Void> markAllRead() {
        notifyService.markAllRead(current().userId());
        return ResVo.ok();
    }

    private static LoginUser current() {
        return ReqContext.loginUser();
    }
}
