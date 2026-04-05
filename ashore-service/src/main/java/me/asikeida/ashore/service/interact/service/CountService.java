package me.asikeida.ashore.service.interact.service;

/**
 * 面经计数：Redis hash（ashore:count:article）实时读写，数据库为可重建的事实源。
 * <p>读取时字段缺失会触发懒加载兜底（从聚合事实源计算后回填 hash）。</p>
 */
public interface CountService {

    String incrPraise(Long articleId, long delta);

    String incrCollect(Long articleId, long delta);

    String incrComment(Long articleId, long delta);

    String incrRead(Long articleId, long delta);

    /** 读取四项计数（缺失字段懒加载兜底） */
    Counts counts(Long articleId);

    record Counts(long praise, long collect, long comment, long read) {
    }
}
