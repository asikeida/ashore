package me.asikeida.ashore.service.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.StatusEnum;
import me.asikeida.ashore.api.vo.report.ReportItemVo;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.article.service.ArticleService;
import me.asikeida.ashore.service.article.service.CommentService;
import me.asikeida.ashore.service.interact.repository.entity.CommentDO;
import me.asikeida.ashore.service.interact.repository.mapper.CommentMapper;
import me.asikeida.ashore.service.report.repository.entity.ReportDO;
import me.asikeida.ashore.service.report.repository.mapper.ReportMapper;
import me.asikeida.ashore.service.report.service.ReportService;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 举报实现：submit 校验目标存在并对待处理去重；handle 编排处置
 * （ARTICLE→下线 / COMMENT→软删）与举报状态流转，事务保证同成败。
 */
@Service
public class ReportServiceImpl implements ReportService {

    private static final int LIST_LIMIT = 100;

    private final ReportMapper reportMapper;

    private final ArticleMapper articleMapper;

    private final CommentMapper commentMapper;

    private final UserMapper userMapper;

    private final ArticleService articleService;

    private final CommentService commentService;

    public ReportServiceImpl(ReportMapper reportMapper, ArticleMapper articleMapper,
                             CommentMapper commentMapper, UserMapper userMapper,
                             ArticleService articleService, CommentService commentService) {
        this.reportMapper = reportMapper;
        this.articleMapper = articleMapper;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.articleService = articleService;
        this.commentService = commentService;
    }

    @Override
    public void submit(LoginUser reporter, String targetType, Long targetId, String reason) {
        if (!ReportDO.TYPE_ARTICLE.equals(targetType) && !ReportDO.TYPE_COMMENT.equals(targetType)) {
            throw ExceptionUtil.of(StatusEnum.PARAM_ERROR, "举报目标类型不合法");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw ExceptionUtil.of(StatusEnum.PARAM_ERROR, "举报理由不能为空且不超过 500 字");
        }
        requireTarget(targetType, targetId);

        // 幂等：同人对同一目标的待处理举报只保留一条
        Long dup = reportMapper.selectCount(new LambdaQueryWrapper<ReportDO>()
                .eq(ReportDO::getReporterId, reporter.userId())
                .eq(ReportDO::getTargetType, targetType)
                .eq(ReportDO::getTargetId, targetId)
                .eq(ReportDO::getStatus, ReportDO.STATUS_PENDING));
        if (dup > 0) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_STATE, "该内容已在待处理举报中，请勿重复提交");
        }

        ReportDO row = new ReportDO();
        row.setReporterId(reporter.userId());
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setReason(reason.trim());
        row.setStatus(ReportDO.STATUS_PENDING);
        reportMapper.insert(row);
    }

    @Override
    public List<ReportItemVo> adminList(String status, Integer limit) {
        int safeLimit = limit == null ? 50 : Math.min(Math.max(limit, 1), LIST_LIMIT);
        LambdaQueryWrapper<ReportDO> wrapper = new LambdaQueryWrapper<ReportDO>()
                .orderByDesc(ReportDO::getCreateTime)
                .last("LIMIT " + safeLimit);
        if (status != null && !status.isBlank()) {
            wrapper.eq(ReportDO::getStatus, status);
        }
        List<ReportDO> rows = reportMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return List.of();
        }

        // 目标摘要：ARTICLE → 标题；COMMENT → 内容截断 50 字
        Set<Long> userIds = rows.stream().map(ReportDO::getReporterId).collect(Collectors.toSet());
        Map<Long, UserDO> usersById = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds)
                .stream().collect(Collectors.toMap(UserDO::getId, u -> u, (a, b) -> a));

        return rows.stream().map(r -> {
            String preview = previewOf(r);
            String reporterNick = "岸友";
            UserDO reporter = usersById.get(r.getReporterId());
            if (reporter != null && reporter.getNickname() != null) {
                reporterNick = reporter.getNickname();
            }
            return new ReportItemVo(String.valueOf(r.getId()), r.getTargetType(),
                    String.valueOf(r.getTargetId()), preview, r.getReason(), reporterNick,
                    r.getStatus(), r.getCreateTime() == null ? null : r.getCreateTime().toString());
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long reportId, String action, LoginUser admin) {
        ReportDO report = reportMapper.selectById(reportId);
        if (report == null) {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND, "举报不存在");
        }
        if (!ReportDO.STATUS_PENDING.equals(report.getStatus())) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_STATE, "该举报已处理");
        }

        LoginUser adminOperator = new LoginUser(admin.userId(), admin.account(), admin.role());
        if ("OFFLINE".equals(action)) {
            // 处置内容：文章下线（可逆）/ 评论软删
            if (ReportDO.TYPE_ARTICLE.equals(report.getTargetType())) {
                articleService.offline(report.getTargetId(), adminOperator);
            } else {
                commentService.delete(report.getTargetId(), adminOperator);
            }
            report.setStatus(ReportDO.STATUS_RESOLVED);
        } else if ("DISMISS".equals(action)) {
            report.setStatus(ReportDO.STATUS_DISMISSED);
        } else {
            throw ExceptionUtil.of(StatusEnum.PARAM_ERROR, "处理动作不合法");
        }
        report.setHandledBy(admin.userId());
        report.setHandleTime(LocalDateTime.now());
        reportMapper.updateById(report);
    }

    private String previewOf(ReportDO r) {
        if (ReportDO.TYPE_ARTICLE.equals(r.getTargetType())) {
            ArticleDO a = articleMapper.selectById(r.getTargetId());
            return a == null ? "（面经已删除）" : a.getTitle();
        }
        CommentDO c = commentMapper.selectById(r.getTargetId());
        if (c == null || c.getDeleted() == 1) {
            return "（评论已删除）";
        }
        String content = c.getContent();
        return content.length() > 50 ? content.substring(0, 50) + "…" : content;
    }

    private void requireTarget(String targetType, Long targetId) {
        if (ReportDO.TYPE_ARTICLE.equals(targetType)) {
            ArticleDO a = articleMapper.selectOne(new LambdaQueryWrapper<ArticleDO>()
                    .eq(ArticleDO::getId, targetId)
                    .eq(ArticleDO::getDeleted, 0));
            if (a == null) {
                throw ExceptionUtil.of(StatusEnum.NOT_FOUND, "被举报的面经不存在");
            }
            return;
        }
        CommentDO c = commentMapper.selectById(targetId);
        if (c == null || c.getDeleted() == 1) {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND, "被举报的评论不存在");
        }
    }
}
