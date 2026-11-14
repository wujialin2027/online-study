package com.online.study.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 流式问答的线程池（S7 用）。
 *
 * <h3>为什么必须另起一个线程</h3>
 * {@code POST /ai/ask/stream} 返回的是 {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter}，
 * 这个对象一旦返回给容器，响应就进入 SSE 模式，靠它自己的线程把数据推出去。
 * 如果直接在请求线程里跑完整个大模型调用，那么"跑完才返回" —— 用户等 5 秒看到一次性刷出的全文，
 * 流式等于白做。所以活必须扔到别的线程，请求线程立刻返回 emitter。
 *
 * <h3>为什么要单独一个池，不共用 Tomcat 线程</h3>
 * 一次流式问答要占住线程几秒到几十秒（等模型吐字）。共用请求线程池的话，
 * 几个慢问答就能把整个站点的线程吃光 —— 学员连课程列表都打不开。
 * 隔离出来之后，AI 再慢也只影响 AI 接口。
 *
 * <h3>参数为什么这么小</h3>
 * 演示环境（2 核 2G）同时在线的学生只有个位数，4 个核心线程够用；
 * 队列 32 是"排队上限"，超过就直接拒绝 —— 这时确实该拒绝，
 * 因为每个排队请求最终都要花一次模型调用的钱。生产上要按实际并发与成本重算。
 *
 * <h3>为什么线程名要带前缀</h3>
 * 排查问题时看线程栈，{@code ai-stream-3} 一眼能认出是流式问答的线程，
 * 而不是 {@code pool-7-thread-2} 这种看了也不知道是谁的。
 */
@Configuration
public class AiStreamConfig {

    /** 流式问答专用线程池的 Bean 名，注入处用它做限定 */
    public static final String EXECUTOR_BEAN = "aiStreamExecutor";

    @Bean(name = EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor aiStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("ai-stream-");
        // 关闭时等正在推进的流结束，别把用户半截答案掐断
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
