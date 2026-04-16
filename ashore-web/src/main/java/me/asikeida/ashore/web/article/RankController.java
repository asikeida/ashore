package me.asikeida.ashore.web.article;

import java.util.List;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.vo.rank.RankItemVo;
import me.asikeida.ashore.service.interact.service.RankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活跃度排行端点：日榜/月榜 Top N（公开）。
 */
@Tag(name = "排行", description = "用户活跃度日榜与月榜")
@RestController
@RequestMapping("/api/v1/rank")
public class RankController {

    private final RankService rankService;

    public RankController(RankService rankService) {
        this.rankService = rankService;
    }

    @Operation(summary = "日榜", description = "当日活跃度 Top N；发文+10/评论+3/点赞收藏±2/阅读+1")
    @GetMapping("/daily")
    public ResVo<List<RankItemVo>> daily(@RequestParam(defaultValue = "20") int limit) {
        return ResVo.ok(rankService.dailyTop(limit));
    }

    @Operation(summary = "月榜", description = "当月活跃度 Top N")
    @GetMapping("/monthly")
    public ResVo<List<RankItemVo>> monthly(@RequestParam(defaultValue = "20") int limit) {
        return ResVo.ok(rankService.monthlyTop(limit));
    }
}
