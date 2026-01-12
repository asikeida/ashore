package me.asikeida.ashore;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * MyBatis-Plus 装配冒烟测试：验证自动配置产出 MP 的 SqlSessionFactory（MybatisConfiguration），
 * 且 @Mapper 接口可被自动扫描并真实执行 SQL。
 * 背景：MP 3.5.18 在 Spring Boot 4.1.1 下自动配置不生效（SqlSessionFactory 缺失），
 * 因此项目使用 Spring Boot 3.5.x，见 docs/开发规划/01-工程约定与DocWeave迁移方案.md。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wiring;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class MybatisPlusWiringTests {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private WiringMapper wiringMapper;

    @Test
    void sqlSessionFactoryIsConfiguredByMybatisPlus() {
        assertThat(sqlSessionFactory).isNotNull();
        assertThat(sqlSessionFactory.getConfiguration()).isInstanceOf(MybatisConfiguration.class);
    }

    @Test
    void mapperAutoScanAndSqlExecution() {
        assertThat(wiringMapper.one()).isEqualTo(1);
    }
}
