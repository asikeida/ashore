package me.asikeida.ashore.web.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.context.ReqContext;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.profile.MeArticleItemVo;
import me.asikeida.ashore.api.vo.profile.MeFootItemVo;
import me.asikeida.ashore.api.vo.profile.MeInterviewItemVo;
import me.asikeida.ashore.core.permission.Permission;
import me.asikeida.ashore.service.profile.service.MeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心端点：我的收藏 / 我的点赞 / 我的 AI 面试会话。
 * <p>全部为本人视角（身份取自拦截器写入的 LoginUser），无越权参数。</p>
 */
@Tag(name = "个人中心", description = "我的收藏、我的点赞与 AI 面试记录（复习场景）")
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @Operation(summary = "我的收藏", description = "收藏的面经卡片，按收藏时间倒序（复习入口）")
    @Permission
    @GetMapping("/collects")
    public ResVo<List<MeFootItemVo>> collects() {
        LoginUser user = ReqContext.loginUser();
        return ResVo.ok(meService.myCollects(user.userId()));
    }

    @Operation(summary = "我的点赞", description = "点赞过的面经卡片，按点赞时间倒序")
    @Permission
    @GetMapping("/praises")
    public ResVo<List<MeFootItemVo>> praises() {
        LoginUser user = ReqContext.loginUser();
        return ResVo.ok(meService.myPraises(user.userId()));
    }

    @Operation(summary = "我的面试记录", description = "AI 面试会话列表（最近活跃在前），点击可回看完整对话")
    @Permission
    @GetMapping("/interviews")
    public ResVo<List<MeInterviewItemVo>> interviews() {
        LoginUser user = ReqContext.loginUser();
        return ResVo.ok(meService.myInterviews(user.userId()));
    }

    @Operation(summary = "我的面经", description = "我发布的面经（含审核状态与阅读/点赞/收藏/评论计数），创建时间倒序——作者的作品全景")
    @Permission
    @GetMapping("/articles")
    public ResVo<List<MeArticleItemVo>> myArticles() {
        LoginUser user = ReqContext.loginUser();
        return ResVo.ok(meService.myArticles(user.userId()));
    }
}
