package me.asikeida.ashore.service.interact.service;

import java.util.List;
import me.asikeida.ashore.api.enums.NotifyTypeEnum;
import me.asikeida.ashore.api.vo.notice.NoticeItemVo;

/**
 * 通知查询与已读：仅操作当前登录用户自己的通知。
 */
public interface NotifyService {

    /**
     * 通知列表（新到旧）。
     *
     * @param type 类型筛选，可空
     * @param page 页码（1 起）
     * @param size 页大小，缺省 20，上限 50
     */
    List<NoticeItemVo> list(Long userId, NotifyTypeEnum type, int page, int size);

    long unreadCount(Long userId);

    /** 全部标记已读 */
    void markAllRead(Long userId);
}
