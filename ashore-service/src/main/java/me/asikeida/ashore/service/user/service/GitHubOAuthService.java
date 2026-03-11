package me.asikeida.ashore.service.user.service;

import me.asikeida.ashore.api.context.LoginUser;

/**
 * GitHub OAuth 登录（授权码流程手动实现，不引 OAuth 客户端库）。
 * <p>凭证未配置时 {@link #isConfigured()} 为 false，端点应拒绝（503001）。</p>
 */
public interface GitHubOAuthService {

    /** 是否已配置凭证（client-id 非空） */
    boolean isConfigured();

    /**
     * 构建授权跳转地址（authorize + scope=read:user + state）。
     *
     * @param state CSRF 防护随机串（回调时与 Cookie 比对）
     */
    String buildAuthorizeUrl(String state);

    /**
     * 授权码换 access_token → 拉取 GitHub 用户 → 查找或创建本地用户（幂等）。
     * <p>OAuth 用户特征：account = "gh" + githubId，password_hash 为空。</p>
     */
    LoginUser loginWithGitHub(String code);

    /**
     * 按 GitHub 用户查找或创建本地用户（幂等，并发绑定由唯一索引兜底）。
     * 独立暴露便于测试与将来的账号绑定场景。
     */
    LoginUser findOrCreateGitHubUser(GitHubUser githubUser);
}
