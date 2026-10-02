package me.asikeida.ashore.service.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import me.asikeida.ashore.api.enums.FootTypeEnum;
import me.asikeida.ashore.api.vo.profile.MeArticleItemVo;
import me.asikeida.ashore.api.vo.profile.MeFootItemVo;
import me.asikeida.ashore.api.vo.profile.MeInterviewItemVo;
import me.asikeida.ashore.api.vo.post.PostItemVo;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import me.asikeida.ashore.service.interact.repository.entity.InterviewSessionDO;
import me.asikeida.ashore.service.interact.repository.mapper.InterviewSessionMapper;
import me.asikeida.ashore.service.article.service.ArticleService;
import me.asikeida.ashore.service.interact.repository.entity.UserFootDO;
import me.asikeida.ashore.service.interact.repository.mapper.UserFootMapper;
import me.asikeida.ashore.service.profile.service.MeService;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import org.springframework.stereotype.Service;

/**
 * 个人中心实现：foot/session 两次批量查询取文章卡片，杜绝逐条 N+1。
 * 足迹按动作时间（update_time）倒序——最近复习的动作排最前。
 */
@Service
public class MeServiceImpl implements MeService {

    private static final int FOOT_LIMIT = 50;
    private static final int INTERVIEW_LIMIT = 20;

    private final UserFootMapper footMapper;

    private final ArticleMapper articleMapper;

    private final InterviewSessionMapper interviewSessionMapper;

    private final ArticleService articleService;

    private final me.asikeida.ashore.service.interact.service.CountService countService;

    private final me.asikeida.ashore.service.image.service.ImageUploader imageUploader;

    private final me.asikeida.ashore.service.user.repository.mapper.UserMapper userMapper;

    private final me.asikeida.ashore.service.interact.repository.mapper.AssistantMessageMapper assistantMessageMapper;

    /** 上传业务限额（MB），来自 ashore.upload 配置——业务分闸，multipart 总闸须 ≥ 此值 */
    private final int avatarMaxMb;

    private final int postImageMaxMb;

    public MeServiceImpl(UserFootMapper footMapper, ArticleMapper articleMapper,
                         InterviewSessionMapper interviewSessionMapper, ArticleService articleService,
                         me.asikeida.ashore.service.interact.service.CountService countService,
                         me.asikeida.ashore.service.image.service.ImageUploader imageUploader,
                         me.asikeida.ashore.service.user.repository.mapper.UserMapper userMapper,
                         me.asikeida.ashore.service.interact.repository.mapper.AssistantMessageMapper assistantMessageMapper,
                         @org.springframework.beans.factory.annotation.Value("${ashore.upload.avatar-max-mb:2}") int avatarMaxMb,
                         @org.springframework.beans.factory.annotation.Value("${ashore.upload.post-image-max-mb:5}") int postImageMaxMb) {
        this.footMapper = footMapper;
        this.articleMapper = articleMapper;
        this.interviewSessionMapper = interviewSessionMapper;
        this.articleService = articleService;
        this.countService = countService;
        this.imageUploader = imageUploader;
        this.userMapper = userMapper;
        this.assistantMessageMapper = assistantMessageMapper;
        this.avatarMaxMb = avatarMaxMb;
        this.postImageMaxMb = postImageMaxMb;
    }

    @Override
    public List<MeFootItemVo> myCollects(Long userId) {
        return myFoots(userId, FootTypeEnum.COLLECT);
    }

    @Override
    public List<MeFootItemVo> myPraises(Long userId) {
        return myFoots(userId, FootTypeEnum.PRAISE);
    }

    private List<MeFootItemVo> myFoots(Long userId, FootTypeEnum type) {
        List<UserFootDO> foots = footMapper.selectList(new LambdaQueryWrapper<UserFootDO>()
                .eq(UserFootDO::getUserId, userId)
                .eq(UserFootDO::getType, type.name())
                .eq(UserFootDO::getState, 1)
                .orderByDesc(UserFootDO::getUpdateTime)
                .last("LIMIT " + FOOT_LIMIT));
        if (foots.isEmpty()) {
            return List.of();
        }
        Map<Long, PostItemVo> cardById = articleService.listByIds(
                        foots.stream().map(UserFootDO::getArticleId).toList()).stream()
                .collect(Collectors.toMap(v -> Long.parseLong(v.id()), Function.identity()));
        return foots.stream()
                .filter(f -> cardById.containsKey(f.getArticleId()))
                .map(f -> {
                    PostItemVo card = cardById.get(f.getArticleId());
                    return new MeFootItemVo(card.id(), card.title(), card.tags(),
                            card.authorNickname(),
                            f.getUpdateTime() == null ? null : f.getUpdateTime().toString());
                })
                .toList();
    }

    @Override
    public List<MeInterviewItemVo> myInterviews(Long userId) {
        List<InterviewSessionDO> sessions = interviewSessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSessionDO>()
                        .eq(InterviewSessionDO::getUserId, userId)
                        .orderByDesc(InterviewSessionDO::getUpdateTime)
                        .last("LIMIT " + INTERVIEW_LIMIT));
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, ArticleDO> articleById = articleMapper.selectBatchIds(
                        sessions.stream().map(InterviewSessionDO::getArticleId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ArticleDO::getId, a -> a, (a, b) -> a));
        return sessions.stream()
                .map(s -> new MeInterviewItemVo(
                        String.valueOf(s.getId()),
                        String.valueOf(s.getArticleId()),
                        articleById.containsKey(s.getArticleId())
                                ? articleById.get(s.getArticleId()).getTitle() : "（面经已删除）",
                        s.getQuestionCount(),
                        s.getStatus(),
                        s.getCreateTime() == null ? null : s.getCreateTime().toString(),
                        s.getUpdateTime() == null ? null : s.getUpdateTime().toString()))
                .toList();
    }

    @Override
    public List<MeArticleItemVo> myArticles(Long userId) {
        // 作者视角：不过滤状态——REVIEW/OFFLINE 也要能看到（这正是审核进度的意义）；
        // 软删除的不展示（作者视角里"删除"即不存在）
        List<ArticleDO> articles = articleMapper.selectList(new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getAuthorId, userId)
                .eq(ArticleDO::getDeleted, 0)
                .orderByDesc(ArticleDO::getCreateTime)
                .last("LIMIT " + FOOT_LIMIT));
        if (articles.isEmpty()) {
            return List.of();
        }
        // 标签批量组装（复用既有防 N+1 路径）+ 计数批量取（CountService 逐篇走 Redis hash，
        // 面经数量有上限 50，可接受；不做跨 hash 批量——复杂度不划算）
        List<Long> ids = articles.stream().map(ArticleDO::getId).toList();
        Map<Long, List<String>> tagsByArticle = articleService.tagsFor(ids);
        return articles.stream()
                .map(a -> {
                    var counts = countService.counts(a.getId());
                    return new MeArticleItemVo(
                            String.valueOf(a.getId()),
                            a.getTitle(),
                            tagsByArticle.getOrDefault(a.getId(), List.of()),
                            a.getStatus(),
                            a.getCreateTime() == null ? null : a.getCreateTime().toString(),
                            counts.read(), counts.praise(), counts.collect(), counts.comment());
                })
                .toList();
    }

    @Override
    public String avatarOf(Long userId) {
        UserDO user = userMapper.selectById(userId);
        return user == null ? null : user.getAvatar();
    }

    @Override
    public List<me.asikeida.ashore.api.vo.profile.AssistantRecordVo> assistantRecords(Long userId) {
        // 最近 200 条消息（跨该用户全部面经），按面经分组聚合出轮数/最近时间/最后一条
        List<me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO> rows =
                assistantMessageMapper.selectList(new LambdaQueryWrapper<me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO>()
                        .eq(me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO::getUserId, userId)
                        .orderByDesc(me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO::getCreateTime)
                        .last("LIMIT 200"));
        if (rows.isEmpty()) {
            return List.of();
        }
        var articleById = articleMapper.selectBatchIds(
                        rows.stream().map(me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO::getArticleId).distinct().toList())
                .stream().collect(Collectors.toMap(ArticleDO::getId, a -> a, (a, b) -> a));
        // LinkedHashMap 保序（rows 已按时间倒序 → 分组首次出现序即最近活跃序）
        Map<Long, List<me.asikeida.ashore.service.interact.repository.entity.AssistantMessageDO>> byArticle =
                new java.util.LinkedHashMap<>();
        for (var r : rows) {
            byArticle.computeIfAbsent(r.getArticleId(), k -> new java.util.ArrayList<>()).add(r);
        }
        return byArticle.entrySet().stream()
                .map(e -> {
                    var list = e.getValue();
                    var last = list.get(0);
                    var article = articleById.get(e.getKey());
                    return new me.asikeida.ashore.api.vo.profile.AssistantRecordVo(
                            String.valueOf(e.getKey()),
                            article == null ? "（面经已删除）" : article.getTitle(),
                            list.size(),
                            last.getCreateTime() == null ? null : last.getCreateTime().toString(),
                            last.getContent().length() > 80 ? last.getContent().substring(0, 80) + "…" : last.getContent());
                })
                .toList();
    }

    @Override
    public String updateAvatar(Long userId, org.springframework.web.multipart.MultipartFile file) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (java.io.IOException e) {
            throw me.asikeida.ashore.api.exception.ExceptionUtil.of(
                    me.asikeida.ashore.api.resp.StatusEnum.UPLOAD_INVALID);
        }
        String url = uploadImage(data, file.getOriginalFilename(), avatarMaxMb * 1024 * 1024, "avatar");
        UserDO patch = new UserDO();
        patch.setId(userId);
        patch.setAvatar(url);
        userMapper.updateById(patch);
        return url;
    }

    @Override
    public String uploadPostImage(Long userId, org.springframework.web.multipart.MultipartFile file) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (java.io.IOException e) {
            throw me.asikeida.ashore.api.exception.ExceptionUtil.of(
                    me.asikeida.ashore.api.resp.StatusEnum.UPLOAD_INVALID);
        }
        return uploadImage(data, file.getOriginalFilename(), postImageMaxMb * 1024 * 1024, "post");
    }

    /** 魔数校验 + 大小限制 + 上传。魔数判定防止改扩展名伪装（jpg→exe 之类） */
    private String uploadImage(byte[] data, String filename, int maxBytes, String domain) {
        if (data == null || data.length == 0 || data.length > maxBytes) {
            throw me.asikeida.ashore.api.exception.ExceptionUtil.of(
                    me.asikeida.ashore.api.resp.StatusEnum.UPLOAD_INVALID);
        }
        String ext = detectExt(data);
        if (ext == null) {
            // 诊断日志：记录文件头 12 字节十六进制，定位用户实际上传的格式
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(12, data.length); i++) {
                hex.append(String.format("%02X ", data[i]));
            }
            log.warn("无法识别的图片格式 domain={} filename={} size={} head=[{}]",
                    domain, filename, data.length, hex.toString().trim());
            String name = filename == null ? "" : filename.toLowerCase();
            if (name.endsWith(".heic") || name.endsWith(".heif") || name.endsWith(".avif")) {
                throw me.asikeida.ashore.api.exception.ExceptionUtil.of(
                        me.asikeida.ashore.api.resp.StatusEnum.UPLOAD_INVALID,
                        "不支持 HEIC/AVIF 格式（常见于 iPhone 原图），请转为 JPG 或 PNG 后上传");
            }
            throw me.asikeida.ashore.api.exception.ExceptionUtil.of(
                    me.asikeida.ashore.api.resp.StatusEnum.UPLOAD_INVALID,
                    "无法识别的图片格式，请上传 PNG/JPG/WEBP/GIF");
        }
        return imageUploader.upload(data, ext, domain);
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(MeServiceImpl.class);

    /** 按文件头魔数识别图片类型；不认识的一律拒绝。所有字节比较必须 & 0xFF（byte 有符号） */
    private String detectExt(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && (d[1] & 0xFF) == 0x50
                && (d[2] & 0xFF) == 0x4E && (d[3] & 0xFF) == 0x47) {
            return "png";
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (d.length >= 12 && (d[0] & 0xFF) == 0x52 && (d[1] & 0xFF) == 0x49
                && (d[2] & 0xFF) == 0x46 && (d[3] & 0xFF) == 0x46
                && (d[8] & 0xFF) == 0x57 && (d[9] & 0xFF) == 0x45
                && (d[10] & 0xFF) == 0x42 && (d[11] & 0xFF) == 0x50) {
            return "webp";
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0x47 && (d[1] & 0xFF) == 0x49 && (d[2] & 0xFF) == 0x46) {
            return "gif";
        }
        return null;
    }
}
