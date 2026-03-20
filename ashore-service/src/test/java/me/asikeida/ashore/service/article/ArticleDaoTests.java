package me.asikeida.ashore.service.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.asikeida.ashore.api.enums.ArticleStatus;
import me.asikeida.ashore.service.ServiceDaoTestBootstrap;
import me.asikeida.ashore.service.article.repository.entity.ArticleDO;
import me.asikeida.ashore.service.article.repository.entity.ArticleDetailDO;
import me.asikeida.ashore.service.article.repository.mapper.ArticleDetailMapper;
import me.asikeida.ashore.service.article.repository.mapper.ArticleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 面经域数据访问测试：V4 建表、状态机流转矩阵、正文版本多行。
 */
@SpringBootTest(classes = ServiceDaoTestBootstrap.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:articledao;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ashore.session.jwt-secret=test-secret",
        "ashore.session.expire-days=30"
})
class ArticleDaoTests {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleDetailMapper detailMapper;

    @Test
    void insertArticleWithDefaultsAndQueryByStatus() {
        ArticleDO article = new ArticleDO();
        article.setTitle("字节跳动后端一面面经");
        article.setAuthorId(1L);
        article.setStatus(ArticleStatus.REVIEW.name());
        articleMapper.insert(article);

        assertThat(article.getId()).isNotNull();
        ArticleDO loaded = articleMapper.selectById(article.getId());
        assertThat(loaded.getDeleted()).isZero();
        assertThat(loaded.getToppingStat()).isZero();

        Long onlineCount = articleMapper.selectCount(new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getStatus, ArticleStatus.ONLINE.name()));
        assertThat(onlineCount).isZero();
    }

    @Test
    void articleDetailKeepsMultipleVersions() {
        ArticleDO article = new ArticleDO();
        article.setTitle("版本化测试");
        article.setAuthorId(1L);
        article.setStatus(ArticleStatus.REVIEW.name());
        articleMapper.insert(article);

        detailMapper.insert(detail(article.getId(), "第一版正文", 1));
        detailMapper.insert(detail(article.getId(), "第二版正文", 2));

        // (article_id, version) 唯一索引拒绝同版本重复插入
        assertThatThrownBy(() -> detailMapper.insert(detail(article.getId(), "重复版本", 1)))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);

        ArticleDetailDO latest = detailMapper.selectOne(new LambdaQueryWrapper<ArticleDetailDO>()
                .eq(ArticleDetailDO::getArticleId, article.getId())
                .orderByDesc(ArticleDetailDO::getVersion)
                .last("LIMIT 1"));
        assertThat(latest.getContent()).isEqualTo("第二版正文");
    }

    @Test
    void statusMachineTransitionMatrix() {
        // 合法流转
        assertThat(ArticleStatus.OFFLINE.canTransitionTo(ArticleStatus.REVIEW)).isTrue();
        assertThat(ArticleStatus.REVIEW.canTransitionTo(ArticleStatus.ONLINE)).isTrue();
        assertThat(ArticleStatus.REVIEW.canTransitionTo(ArticleStatus.OFFLINE)).isTrue();
        assertThat(ArticleStatus.ONLINE.canTransitionTo(ArticleStatus.OFFLINE)).isTrue();
        assertThat(ArticleStatus.ONLINE.canTransitionTo(ArticleStatus.REVIEW)).isTrue();
        // 不可达：未审核不得直接上线
        assertThat(ArticleStatus.OFFLINE.canTransitionTo(ArticleStatus.ONLINE)).isFalse();
        // 同态与未知值
        assertThat(ArticleStatus.REVIEW.canTransitionTo(ArticleStatus.REVIEW)).isFalse();
        assertThatThrownBy(() -> ArticleStatus.of("WEIRD")).isInstanceOf(IllegalArgumentException.class);
    }

    private ArticleDetailDO detail(Long articleId, String content, int version) {
        ArticleDetailDO d = new ArticleDetailDO();
        d.setArticleId(articleId);
        d.setContent(content);
        d.setVersion(version);
        return d;
    }
}
