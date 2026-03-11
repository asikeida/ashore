package me.asikeida.ashore.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.asikeida.ashore.api.context.LoginUser;
import me.asikeida.ashore.service.ServiceDaoTestBootstrap;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import me.asikeida.ashore.service.user.service.GitHubOAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * GitHub OAuth 本地用户查找/创建测试（幂等绑定）。
 * loginWithGitHub 的 HTTP 段（换 token/拉用户）不做本地测试，由真实凭证 E2E 覆盖。
 */
@SpringBootTest(classes = ServiceDaoTestBootstrap.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:githubuser;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=test-secret",
        "ashore.session.expire-days=30"
})
class GitHubUserServiceTests {

    @Autowired
    private GitHubOAuthService githubOAuthService;

    @Autowired
    private UserMapper userMapper;

    @Test
    void findOrCreateIsIdempotentForSameGithubUser() {
        LoginUser first = githubOAuthService.findOrCreateGitHubUser(
                new me.asikeida.ashore.service.user.service.GitHubUser(987654321L, "octocat", "Octo Cat"));

        assertThat(first.userId()).isNotNull();
        assertThat(first.account()).isEqualTo("gh987654321");
        assertThat(first.role()).isEqualTo("USER");

        // 同一 GitHub 用户再次登录：复用本地用户，不重复创建
        LoginUser second = githubOAuthService.findOrCreateGitHubUser(
                new me.asikeida.ashore.service.user.service.GitHubUser(987654321L, "octocat", "Octo Cat"));
        assertThat(second.userId()).isEqualTo(first.userId());

        // OAuth 用户特征：password_hash 为空（无法账密登录），github_id 已绑定
        UserDO row = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getGithubId, 987654321L));
        assertThat(row.getPasswordHash()).isNull();
    }
}
