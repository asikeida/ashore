package me.asikeida.ashore.web.user;

import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.user.UserProfileVo;
import me.asikeida.ashore.service.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 他人主页端点（公开）：资料 + 已上线面经 + 汇总数据。
 */
@Tag(name = "用户主页", description = "他人视角的用户资料与作品列表（公开）")
@RestController
@RequestMapping("/api/v1/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @Operation(summary = "用户主页", description = "公开；资料 + 已上线面经（置顶优先）+ 阅读/获赞汇总")
    @GetMapping("/{id}")
    public ResVo<UserProfileVo> profile(@PathVariable Long id) {
        return ResVo.ok(userProfileService.profile(id));
    }
}
