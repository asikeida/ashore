package me.asikeida.ashore.service.ai.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.StatusEnum;
import me.asikeida.ashore.service.ai.AiChatClient;
import me.asikeida.ashore.service.ai.service.ArticleAssistantService;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.entity.ArticleDetailDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleDetailMapper;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.interact.repository.mapper.AssistantMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 面经 AI 助手实现：系统提示词携带文章标题与正文（截断 6000 字控制 token），
 * 历史由前端回传（不落库），无会话状态。文章仅 ONLINE 可问（与详情可见性一致）。
 */
@Service
public class ArticleAssistantServiceImpl implements ArticleAssistantService {

    private static final int CONTENT_LIMIT = 6000;

    private static final int HISTORY_LIMIT = 10;   // 最多携带最近 10 条历史（5 轮）

    private static final String SYSTEM_PROMPT =
            "你是面经社区 Ashore 常驻面经详情页的 AI 助手。用户正在阅读下面这篇面经，"
            + "请基于文章内容回答提问：解释文中概念、评估复盘质量、模拟面试官追问、给出改进建议。"
            + "文章未覆盖的问题也可以回答，但需注明\"文章未提及，以下是我的补充\"。"
            + "回答用中文，简洁分点，不要寒暄。";

    private final AiChatClient aiChatClient;

    private final ArticleMapper articleMapper;

    private final ArticleDetailMapper detailMapper;

    private final AssistantMessageMapper messageMapper;

    private static final Logger log = LoggerFactory.getLogger(ArticleAssistantServiceImpl.class);

    public ArticleAssistantServiceImpl(AiChatClient aiChatClient, ArticleMapper articleMapper,
                                       ArticleDetailMapper detailMapper, AssistantMessageMapper messageMapper) {
        this.aiChatClient = aiChatClient;
        this.articleMapper = articleMapper;
        this.detailMapper = detailMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public String answerStream(Long articleId, String message, List<AiChatClient.Message> history,
                               Consumer<String> onDelta) {
        ArticleDO article = articleMapper.selectById(articleId);
        // 可见性与详情一致：非 ONLINE 仅作者/管理员可见的逻辑在 detail 端点；
        // 助手简化为"非 ONLINE 一律拒绝"——面板本身只在详情页出现，够用
        if (article == null || article.getDeleted() != 0
                || !ArticleStatus.ONLINE.name().equals(article.getStatus())) {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND);
        }
        String content = latestContent(articleId);
        String articleText = "文章标题：" + article.getTitle() + "\n\n正文：\n"
                + (content.length() > CONTENT_LIMIT ? content.substring(0, CONTENT_LIMIT) + "…（截断）" : content);

        List<AiChatClient.Message> messages = new ArrayList<>();
        messages.add(new AiChatClient.Message("system", SYSTEM_PROMPT + "\n\n" + articleText));
        if (history != null) {
            history.stream()
                    .filter(m -> m != null && ("user".equals(m.role()) || "assistant".equals(m.role())))
                    .limit(HISTORY_LIMIT)
                    .forEach(messages::add);
        }
        messages.add(new AiChatClient.Message("user", message));
        return aiChatClient.streamChat(messages, onDelta);
    }

    private String latestContent(Long articleId) {
        ArticleDetailDO latest = detailMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleDetailDO>()
                        .eq(ArticleDetailDO::getArticleId, articleId)
                        .orderByDesc(ArticleDetailDO::getVersion)
                        .last("LIMIT 1"));
        return latest == null ? "" : latest.getContent();
    }

    @Override
    public void saveTurn(Long userId, Long articleId, String userMessage, String assistantReply) {
        // 旁路语义：落库失败仅告警（对话体验优先于记录完整性；页面会话不受影响）
        try {
            if (userMessage != null && !userMessage.isBlank()) {
                messageMapper.insert(message(userId, articleId, "user", userMessage));
            }
            if (assistantReply != null && !assistantReply.isBlank()) {
                messageMapper.insert(message(userId, articleId, "assistant", assistantReply));
            }
        } catch (Exception e) {
            log.warn("助手对话落库失败 userId={} articleId={}: {}", userId, articleId, e.getMessage());
        }
    }

    @Override
    public List<AiChatClient.Message> history(Long userId, Long articleId) {
        // 新到旧取 100 条，反转为正序返回（对话阅读顺序）
        List<me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO> rows =
                messageMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO>()
                        .eq(me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO::getUserId, userId)
                        .eq(me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO::getArticleId, articleId)
                        .orderByDesc(me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO::getCreateTime)
                        .last("LIMIT 100"));
        java.util.Collections.reverse(rows);
        return rows.stream()
                .map(r -> new AiChatClient.Message(r.getRole(), r.getContent()))
                .toList();
    }

    private me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO message(
            Long userId, Long articleId, String role, String content) {
        me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO m =
                new me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO();
        m.setUserId(userId);
        m.setArticleId(articleId);
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}
