package me.asikeida.ashore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ashore 启动类。组件扫描覆盖 api/core/service/web 全模块。
 * @EnableScheduling 支撑计数定时回写（ArticleCountSyncTask，每 5 分钟）。
 */
@EnableScheduling
@SpringBootApplication
public class AshoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(AshoreApplication.class, args);
    }
}
