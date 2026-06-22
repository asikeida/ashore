package me.asikeida.ashore.service.profile.service;

import java.util.List;
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
}
