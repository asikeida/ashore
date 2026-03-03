package me.asikeida.ashore.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.asikeida.ashore.service.ServiceDaoTestBootstrap;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 用户表数据访问测试：验证 Flyway V1 建表、主源 Mapper 扫描（待办 T1）、
 * 雪花 ID 回填与审计时间填充（AuditMetaObjectHandler）。
 * 使用模块内测试启动类（见 {@link ServiceDaoTestBootstrap}），不经 ashore-web。
 */
@SpringBootTest(classes = ServiceDaoTestBootstrap.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:userdao;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // ashore.session.* 定义在 web 模块 yaml，service 测试自举需自带（SessionServiceImpl @Value 无默认值，生产漏配即 fail-fast）
        "ashore.session.jwt-secret=test-secret",
        "ashore.session.expire-days=30"
})
class UserDaoTests {

    @Autowired
    private UserMapper userMapper;

    @Test
    void insertAssignsSnowflakeIdAndFillsAuditTime() {
        UserDO user = new UserDO();
        user.setAccount("dao-test-" + System.currentTimeMillis());
        user.setNickname("DAO 测试用户");
        user.setRole("USER");
        user.setStatus("ACTIVE");

        userMapper.insert(user);

        assertThat(user.getId()).isNotNull().isPositive();
        UserDO loaded = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getAccount, user.getAccount()));
        assertThat(loaded.getNickname()).isEqualTo("DAO 测试用户");
        assertThat(loaded.getCreateTime()).isNotNull();
        assertThat(loaded.getUpdateTime()).isNotNull();
    }

    @Test
    void uniqueAccountIndexRejectsDuplicate() {
        String account = "dup-" + System.currentTimeMillis();
        UserDO first = new UserDO();
        first.setAccount(account);
        userMapper.insert(first);

        UserDO second = new UserDO();
        second.setAccount(account);
        // 唯一索引兜底：应用层唯一校验（M2-2）之前，数据库也不允许重复
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> userMapper.insert(second))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
}
