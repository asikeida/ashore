package me.asikeida.ashore.service.user.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GitHub 用户信息（来自 https://api.github.com/user）。
 */
public record GitHubUser(Long githubId, String login, String nickname) {

    /** api.github.com/user 的原始响应结构（字段名与 GitHub 对齐） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiUser(Long id, String login, String name) {
    }
}
