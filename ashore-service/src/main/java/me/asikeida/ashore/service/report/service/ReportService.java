package me.asikeida.ashore.service.report.service;

import java.util.List;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.vo.report.ReportItemVo;

/**
 * 举报域：用户提交举报（文章/评论），管理员处理（驳回 或 处置内容）。
 * <p>处置动作复用既有域服务：文章走 ArticleService.offline（下线可逆），
 * 评论走 CommentService.delete（软删）——举报域只编排，不重复实现处置逻辑。</p>
 */
public interface ReportService {

    /** 提交举报：目标存在性校验；同人对同一目标的待处理举报去重（幂等） */
    void submit(LoginUser reporter, String targetType, Long targetId, String reason);

    /** 管理台举报列表：status 可选（PENDING/RESOLVED/DISMISSED），时间倒序 */
    List<ReportItemVo> adminList(String status, Integer limit);

    /**
     * 处理举报：action = DISMISS（驳回）或 OFFLINE（处置内容）。
     * OFFLINE 时：ARTICLE → 下线该文；COMMENT → 软删该评论。举报状态流转为 RESOLVED/DISMISSED。
     */
    void handle(Long reportId, String action, LoginUser admin);
}
