package me.asikeida.ashore.service.article.service;

import java.util.List;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.vo.post.AdminPostItemVo;
import me.asikeida.ashore.api.vo.post.EditPostReq;
import me.asikeida.ashore.api.vo.post.HotPostItemVo;
import me.asikeida.ashore.api.vo.post.PostDetailVo;
import me.asikeida.ashore.api.vo.post.PostItemVo;
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

    /** 批量取面经标签（供搜索等读路径复用，两次批量查询防 N+1） */
    java.util.Map<Long, List<String>> tagsFor(List<Long> articleIds);

    /** 管理台文章列表：按状态筛选（含 REVIEW），软删除除外（仅 ADMIN 调用）；每项含审核状态 */
    List<AdminPostItemVo> adminList(String status, Integer limit);

    /**
     * 热榜：ONLINE 面经按互动热度加权排序（read*1+praise*4+collect*6+comment*8）。
     * 数据源 article_count 聚合表（5 分钟批量回写），非实时；公开无需登录。
     */
    List<HotPostItemVo> hot(Integer limit);

    /**
     * 置顶/取消置顶（仅管理员）：写 topping_stat（列表排序第一键）。
     * 不做状态校验——置顶是运营标记，与审核状态正交（OFFLINE 的文也可预置顶）。
     *
     * @param topped true 置顶 / false 取消
     */
    void setTopping(Long articleId, boolean topped, LoginUser operator);

    /**
     * 搜索索引全量重建：把全部 ONLINE 面经重新写入 ES 当前版本索引。
     * 场景：索引版本升级（v1→v2 换分词器/映射）后迁移存量数据；同步是旁路，单篇失败仅告警。
     *
     * @return 成功写入索引的文档数
     */
    int reindexSearch();

    /**
     * 按 id 批量取面经卡片（个人中心收藏/点赞列表复用，含标签与作者昵称，防 N+1）。
     * 不筛状态：个人足迹里允许出现后来下线的文章（详情可见性仍由 detail 端点把关）。
     */
    List<PostItemVo> listByIds(java.util.Collection<Long> articleIds);
}
