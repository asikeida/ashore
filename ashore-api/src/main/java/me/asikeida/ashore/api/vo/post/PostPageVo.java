package me.asikeida.ashore.api.vo.post;

import java.util.List;

/**
 * 游标分页结果。nextCursor 为空表示没有更多数据；
 * 客户端原样回传 nextCursor 即可取下一页（透明明文：topping_createTime_id）。
 */
public record PostPageVo(String nextCursor, List<PostItemVo> items) {
}
