package me.asikeida.ashore.service.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.List;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.StatusEnum;
import me.asikeida.ashore.api.vo.interview.InterviewMessageVo;
import me.asikeida.ashore.api.vo.interview.InterviewReplyVo;
import me.asikeida.ashore.service.ai.AiChatClient;
import me.asikeida.ashore.service.ai.service.InterviewService;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleDetailMapper;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.interact.repository.entity.InterviewMessageDO;
import me.asikeida.ashore.service.interact.repository.entity.InterviewSessionDO;
import me.asikeida.ashore.service.interact.repository.mapper.InterviewMessageMapper;
import me.asikeida.ashore.service.interact.repository.mapper.InterviewSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 面试官实现：两段 prompt（出题 / 点评追问），消息全量持久化；
 * AI 调用失败时事务回滚（不产生半截会话）。
 */
@Service
public class InterviewServiceImpl implements InterviewService {

    private static final String ROLE_INTERVIEWER = "INTERVIEWER";

    private static final String ROLE_USER = "USER";

    private static final String SYSTEM_PROMPT =
            "你是严格的技术面试官。只输出与面试直接相关的文字，不要寒暄。";

    private final AiChatClient aiChatClient;

    private final ArticleMapper articleMapper;

    private final ArticleDetailMapper detailMapper;

    private final InterviewSessionMapper sessionMapper;

    private final InterviewMessageMapper messageMapper;

    public InterviewServiceImpl(AiChatClient aiChatClient, ArticleMapper articleMapper,
                                ArticleDetailMapper detailMapper, InterviewSessionMapper sessionMapper,
                                InterviewMessageMapper messageMapper) {
        this.aiChatClient = aiChatClient;
        this.articleMapper = articleMapper;
        this.detailMapper = detailMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewReplyVo start(Long articleId, LoginUser candidate) {
        ArticleDO article = articleMapper.selectOne(new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getId, articleId)
                .eq(ArticleDO::getDeleted, 0));
        if (article == null || !ArticleStatus.ONLINE.name().equals(article.getStatus())) {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND);
        }

        // 同一面经同人复用未关闭会话（防误触重复建会话）
        InterviewSessionDO session = sessionMapper.selectOne(new LambdaQueryWrapper<InterviewSessionDO>()
                .eq(InterviewSessionDO::getArticleId, articleId)
                .eq(InterviewSessionDO::getUserId, candidate.userId())
                .eq(InterviewSessionDO::getStatus, "ACTIVE"));
        if (session != null) {
            List<InterviewMessageDO> messages = messagesOf(session.getId());
            InterviewMessageDO last = messages.get(messages.size() - 1);
            return new InterviewReplyVo(String.valueOf(session.getId()),
                    session.getQuestionCount(), last.getContent());
        }

        // 面经原文作为出题上下文（最新版本正文）
        var detail = articleMapper == null ? null : latestContent(articleId);
        String question = aiChatClient.chat(List.of(
                new AiChatClient.Message("system", SYSTEM_PROMPT),
                new AiChatClient.Message("user", "以下是一篇候选人发布的面经（他描述自己经历的面试）。"
                        + "请基于其中提到的技术点，向他提出一个有深度的技术追问，考察真实掌握程度。只输出问题本身。\n\n"
                        + "面经标题：" + article.getTitle() + "\n面经内容：\n" + detail)));

        session = new InterviewSessionDO();
        session.setArticleId(articleId);
        session.setUserId(candidate.userId());
        session.setQuestionCount(1);
        session.setStatus("ACTIVE");
        sessionMapper.insert(session);
        saveMessage(session.getId(), ROLE_INTERVIEWER, question);

        return new InterviewReplyVo(String.valueOf(session.getId()), session.getQuestionCount(), question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewReplyVo answer(Long sessionId, String answer, LoginUser candidate) {
        InterviewSessionDO session = requireOwn(sessionId, candidate);
        saveMessage(session.getId(), ROLE_USER, answer);

        // 携全量历史请 AI 点评+追问
        List<AiChatClient.Message> chat = new ArrayList<>();
        chat.add(new AiChatClient.Message("system", SYSTEM_PROMPT + "候选人刚回答了你的问题。"
                + "请先用一两句点评该回答（指出准确、遗漏或错误之处），再提出下一个技术追问。"));
        for (InterviewMessageDO m : messagesOf(sessionId)) {
            chat.add(new AiChatClient.Message(
                    ROLE_INTERVIEWER.equals(m.getRole()) ? "assistant" : "user", m.getContent()));
        }
        String reply = aiChatClient.chat(chat);

        saveMessage(session.getId(), ROLE_INTERVIEWER, reply);
        session.setQuestionCount(session.getQuestionCount() + 1);
        sessionMapper.updateById(session);

        return new InterviewReplyVo(String.valueOf(session.getId()), session.getQuestionCount(), reply);
    }

    @Override
    public List<InterviewMessageVo> history(Long sessionId, LoginUser candidate) {
        requireOwn(sessionId, candidate);
        return messagesOf(sessionId).stream()
                .map(m -> new InterviewMessageVo(m.getRole(), m.getContent(),
                        m.getCreateTime() == null ? null : m.getCreateTime().toString()))
                .toList();
    }

    // ---------- 内部 ----------

    private InterviewSessionDO requireOwn(Long sessionId, LoginUser candidate) {
        InterviewSessionDO session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(candidate.userId())) {
            throw ExceptionUtil.of(StatusEnum.NOT_FOUND);
        }
        return session;
    }

    private List<InterviewMessageDO> messagesOf(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<InterviewMessageDO>()
                .eq(InterviewMessageDO::getSessionId, sessionId)
                .orderByAsc(InterviewMessageDO::getId));
    }

    private void saveMessage(Long sessionId, String role, String content) {
        InterviewMessageDO m = new InterviewMessageDO();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        messageMapper.insert(m);
    }

    /** 最新版本正文（出题上下文） */
    private String latestContent(Long articleId) {
        var latest = detailMapper.selectOne(new LambdaQueryWrapper<me.asikeida.ashore.service.article.repository.entity.ArticleDetailDO>()
                .eq(me.asikeida.ashore.service.article.repository.entity.ArticleDetailDO::getArticleId, articleId)
                .orderByDesc(me.asikeida.ashore.service.article.repository.entity.ArticleDetailDO::getVersion)
                .last("LIMIT 1"));
        return latest == null ? "" : latest.getContent();
    }
}
