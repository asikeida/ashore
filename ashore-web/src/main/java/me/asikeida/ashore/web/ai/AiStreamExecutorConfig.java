package me.asikeida.ashore.web.ai;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 流式推送线程池：SSE 端点把 AI 调用挪出 Tomcat 工作线程，占用的
 * 是本池线程（AI 响应以分钟计）。有界（4 线程）防并发 AI 调用打爆配额，
 * 队列上限由线程池自然背压（个人项目规模足够）。
 */
@Configuration
public class AiStreamExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService aiStreamExecutor() {
        AtomicInteger seq = new AtomicInteger();
        return Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "ai-stream-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }
}
