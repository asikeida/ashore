package me.asikeida.ashore.api.vo.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建评论请求：parentCommentId 为空 = 发表顶级评论；有值 = 回复该评论（或其子回复）。
 */
public record CreateCommentReq(

        @NotBlank(message = "评论内容不能为空")
        @Size(max = 2000, message = "评论最长 2000 字")
        String content,

        @Size(max = 24, message = "昵称最长 24 字")
        String nickname,

        Long parentCommentId
) {
}
