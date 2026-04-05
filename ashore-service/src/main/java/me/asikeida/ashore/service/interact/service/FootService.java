package me.asikeida.ashore.service.interact.service;

import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.enums.FootTypeEnum;

/**
 * 足迹切换：点赞/收藏的并发幂等写（paicoding compareAndUpdate fixme 的修复落地）。
 * <p>并发控制：Redisson 锁（lock:foot:{uid}:{aid}:{type}）串行化"查-改-写"段，
 * 唯一索引 uk_foot_user_article_type 作为最终防线。</p>
 */
public interface FootService {

    /**
     * 切换点赞/收藏状态（当前有效则取消，已取消则恢复）。
     *
     * @return 切换后的有效状态与该行为最新计数
     */
    ToggleResult toggle(Long articleId, FootTypeEnum type, LoginUser user);

    /** 当前是否处于有效状态 */
    boolean isActive(Long articleId, FootTypeEnum type, Long userId);

    /** 切换结果 */
    record ToggleResult(boolean active, long count) {
    }
}
