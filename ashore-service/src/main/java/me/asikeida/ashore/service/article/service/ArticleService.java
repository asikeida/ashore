package me.asikeida.ashore.service.article.service;

import java.util.List;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.vo.post.EditPostReq;
import me.asikeida.ashore.api.vo.post.PostDetailVo;
import me.asikeida.ashore.api.vo.post.PostPageVo;
import me.asikeida.ashore.api.vo.post.PostVersionVo;
import me.asikeida.ashore.api.vo.post.PublishPostReq;
import me.asikeida.ashore.api.vo.post.PostVo;

/**
 * 面经写域：发布、编辑（版本化）、状态流转。
 * <p>所有操作的身份与权限入参来自鉴权拦截器写入的 {@link LoginUser}。</p>
 */
public interface ArticleService {

    /** 发布：所有人进 REVIEW（审核制），正文落 version=1，标签自动建并绑定 */
    PostVo publish(PublishPostReq req, LoginUser author);

    /**
     * 编辑：仅作者或管理员；每次编辑插入新正文版本（version+1）；
     * 已上线（ONLINE）面经编辑后回到 REVIEW 重新审核，OFFLINE/REVIEW 保持原状态。
     */
    PostVo edit(Long articleId, EditPostReq req, LoginUser operator);

    /** 审核通过上线：仅管理员；REVIEW → ONLINE */
    void online(Long articleId, LoginUser operator);

    /** 下线/撤回：作者或管理员；REVIEW/ONLINE → OFFLINE */
    void offline(Long articleId, LoginUser operator);

    /** 重新提交审核：仅作者；OFFLINE → REVIEW */
    void submit(Long articleId, LoginUser operator);

    /** 详情（M3-3 完整实现，此处为编辑回显复用） */
    PostDetailVo detail(Long articleId, LoginUser operator);

    /**
     * 公开列表：仅 ONLINE 且未删除，三字段游标分页（topping_stat, create_time, id）。
     *
     * @param cursor  上一页返回的 nextCursor；null 表示第一页
     * @param limit   页大小，缺省 20，上限 50
     * @param tagName 标签名筛选，可空
     */
    PostPageVo list(String cursor, Integer limit, String tagName);

    /** 正文版本历史（作者/管理员；新版本在前） */
    List<PostVersionVo> versions(Long articleId, LoginUser operator);
}
