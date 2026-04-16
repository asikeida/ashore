package me.asikeida.ashore.service.interact.service;

import java.util.List;
import me.asikeida.ashore.api.enums.RankActionEnum;
import me.asikeida.ashore.api.vo.rank.RankItemVo;

/**
 * 活跃度排行：Redis zset 日榜/月榜，Lua 脚本原子化"加分 + 首次写 TTL"。
 * <p>修复 paicoding UserActivityRankServiceImpl 的 TTL 竞态缺陷（先判后设非原子，
 * 进程在两步间崩溃会产生永不过期键）。</p>
 */
public interface RankService {

    /**
     * 记录一次排行动作（幂等：正动作当日按（用户，动作，面经）去重；取消动作需当日已有正记录）。
     * best-effort：Redis 不可达时仅告警不反压业务。
     */
    void record(Long userId, RankActionEnum action, Long articleId);

    /** 日榜 Top N（含昵称） */
    List<RankItemVo> dailyTop(int limit);

    /** 月榜 Top N（含昵称） */
    List<RankItemVo> monthlyTop(int limit);
}
