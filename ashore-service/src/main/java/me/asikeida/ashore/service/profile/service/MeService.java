package me.asikeida.ashore.service.profile.service;

import java.util.List;
import me.asikeida.ashore.api.vo.profile.MeArticleItemVo;
import me.asikeida.ashore.api.vo.profile.MeFootItemVo;
import me.asikeida.ashore.api.vo.profile.MeInterviewItemVo;

/**
 * 个人中心读域：我的足迹（收藏/点赞）与我的 AI 面试会话。
 * <p>数据视角为"本人可见"：不筛文章状态（下线文章仍出现在足迹里，
 * 详情可见性由 detail 端点把关）。</p>
 */
public interface MeService {

    /** 我的收藏（state=1，按动作时间倒序，上限 50） */
    List<MeFootItemVo> myCollects(Long userId);

    /** 我的点赞（state=1，按动作时间倒序，上限 50） */
    List<MeFootItemVo> myPraises(Long userId);

    /** 我的 AI 面试会话（按最近活跃倒序，上限 20） */
    List<MeInterviewItemVo> myInterviews(Long userId);

    /** 我发布的面经（含审核状态与四项计数，创建时间倒序，上限 50）——作者的作品全景 */
    List<MeArticleItemVo> myArticles(Long userId);

    /** 我的头像 URL；未设置返回 null */
    String avatarOf(Long userId);

    /**
     * 上传头像：校验（类型/大小）→ ImageUploader 上传 → user.avatar 写 URL。
     *
     * @return 头像公网 URL
     */
    String updateAvatar(Long userId, org.springframework.web.multipart.MultipartFile file);

    /** 上传文章配图：校验（类型/≤5MB）→ 上传；不落库（URL 由编辑器写入正文 Markdown） */
    String uploadPostImage(Long userId, org.springframework.web.multipart.MultipartFile file);

    /** AI 助手记录：按面经分组（轮数/最近时间/最后一条消息），最近活跃在前 */
    List<me.asikeida.ashore.api.vo.profile.AssistantRecordVo> assistantRecords(Long userId);
}
