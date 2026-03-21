package me.asikeida.ashore.api.vo.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 发布面经请求。
 */
public record PublishPostReq(

        @NotBlank(message = "标题不能为空")
        @Size(min = 5, max = 128, message = "标题长度需 5-128 字")
        String title,

        @NotBlank(message = "正文不能为空")
        @Size(max = 50000, message = "正文最长 50000 字")
        String content,

        /** 标签名列表，可空；最多 5 个，不存在的标签自动创建 */
        @Size(max = 5, message = "标签最多 5 个")
        List<String> tags
) {
}
