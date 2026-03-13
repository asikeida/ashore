package me.asikeida.ashore.service.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import me.asikeida.ashore.service.user.service.GitHubUser;
import me.asikeida.ashore.service.user.service.GitHubOAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * GitHub OAuth 授权码流程实现。HTTP 调用来自应用宿主机（本地开发直连 GitHub 可用；
 * 部署到国内服务器时需要为 api.github.com 配置代理，属部署议题）。
 */
@Service
public class GitHubOAuthServiceImpl implements GitHubOAuthService {

    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_API = "https://api.github.com/user";

    private final UserMapper userMapper;

    private final RestClient restClient;

    @Value("${ashore.oauth.github.client-id:}")
    private String clientId;

    @Value("${ashore.oauth.github.client-secret:}")
    private String clientSecret;

    @Value("${ashore.oauth.github.redirect-uri:}")
    private String redirectUri;

    public GitHubOAuthServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        return UriComponentsBuilder.fromHttpUrl(AUTHORIZE_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "read:user")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    @Override
    public LoginUser loginWithGitHub(String code) {
        String accessToken = exchangeAccessToken(code);
        GitHubUser.ApiUser apiUser = fetchUser(accessToken);
        GitHubUser ghUser = new GitHubUser(apiUser.id(), apiUser.login(),
                apiUser.name() == null || apiUser.name().isBlank() ? apiUser.login() : apiUser.name());
        return findOrCreateGitHubUser(ghUser);
    }

    /** 查找或创建本地用户（幂等）：account = "gh" + githubId，password_hash 留空标识 OAuth 用户 */
    @Override
    public LoginUser findOrCreateGitHubUser(GitHubUser ghUser) {
        UserDO existing = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getGithubId, ghUser.githubId()));
        if (existing != null) {
            return new LoginUser(existing.getId(), existing.getAccount(), existing.getRole());
        }

        UserDO user = new UserDO();
        user.setAccount("gh" + ghUser.githubId());
        user.setNickname(ghUser.nickname());
        user.setGithubId(ghUser.githubId());
        user.setRole(UserDO.ROLE_USER);
        user.setStatus(UserDO.STATUS_ACTIVE);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发绑定兜底：唯一索引拦截后按 githubId 重新读取
            UserDO raced = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                    .eq(UserDO::getGithubId, ghUser.githubId()));
            if (raced == null) {
                throw e;
            }
            return new LoginUser(raced.getId(), raced.getAccount(), raced.getRole());
        }
        return new LoginUser(user.getId(), user.getAccount(), user.getRole());
    }

    private String exchangeAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        TokenResponse token = restClient.post()
                .uri(TOKEN_URL)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
            throw new IllegalStateException("GitHub 授权码换取 token 失败");
        }
        return token.accessToken();
    }

    private GitHubUser.ApiUser fetchUser(String accessToken) {
        return restClient.get()
                .uri(USER_API)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GitHubUser.ApiUser.class);
    }

    /** /access_token 响应（Accept: application/json） */
    record TokenResponse(String accessToken) {
    }
}
