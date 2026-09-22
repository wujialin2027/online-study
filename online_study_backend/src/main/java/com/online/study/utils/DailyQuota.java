package com.online.study.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内「按自然日计数」的限额器。
 *
 * <h3>为什么不用 Redis 做</h3>
 * 这是本项目的技术取舍：整个系统是单机单进程部署（2G 内存的轻量服务器），
 * 计数只在自己进程里用，没有多实例共享的需求。引一个 Redis 只为存几个计数器，
 * 换来的是多一个要装、要监控、要多占几十兆内存的中间件 —— 不划算。
 * 如果将来这个服务扩成多实例，把 {@link #tryAcquire} 的实现换成
 * {@code INCR} + {@code EXPIRE} 即可，调用方一行都不用改。
 *
 * <h3>为什么用 ConcurrentHashMap + CAS 而不是 synchronized</h3>
 * "/ai/ask" 这个接口本身要等大模型好几秒，是慢接口 —— 计数这点开销相对可以忽略。
 * 但计数发生在请求的最前面，如果用一把大锁，所有并发请求都要在入口排队，
 * 高并发时反而成为瓶颈。这里用 {@link AtomicInteger#compareAndSet} 做无锁自增，
 * 只在跨天清零时才短暂加锁，正常路径完全不阻塞。
 *
 * <h3>内存会不会涨爆</h3>
 * 键是「业务前缀 + 角色 + 用户 ID」，一个账号一个键，量级等于用户数，
 * 而且每天清零时整个 Map 会被丢弃，不会被历史账号长期占用。
 * 真有几十万账号的话应该换成带过期的缓存（Caffeine / Redis），当前规模不需要。
 */
@Component
public class DailyQuota {

    /** 键 → 当天已用次数 */
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /** 计数器当前所属的日期；日期一变就整体清零 */
    private volatile LocalDate currentDay = LocalDate.now();

    /**
     * 尝试占用一次额度。
     *
     * @param bucket 业务键，建议形如 {@code "ai:student:12"}，把"哪个功能 + 谁"写进去
     * @param limit  当天上限（&lt;= 0 视为不限制）
     * @return true = 放行（已计数）；false = 今天已用完
     */
    public boolean tryAcquire(String bucket, int limit) {
        if (limit <= 0) {
            return true;
        }
        rollIfNewDay();
        AtomicInteger counter = counters.computeIfAbsent(bucket, key -> new AtomicInteger());
        while (true) {
            int used = counter.get();
            if (used >= limit) {
                return false;
            }
            // CAS：只有"当前值还是 used"时才把它改成 used+1，否则重读再试。
            // 这样并发下既不会超发，也不用给整个方法加锁。
            if (counter.compareAndSet(used, used + 1)) {
                return true;
            }
        }
    }

    /** 当天已用次数（用于给前端展示"今天还剩几次"） */
    public int used(String bucket) {
        rollIfNewDay();
        AtomicInteger counter = counters.get(bucket);
        return counter == null ? 0 : counter.get();
    }

    /** 当天剩余次数 */
    public int remaining(String bucket, int limit) {
        return Math.max(0, limit - used(bucket));
    }

    /**
     * 跨天清零。
     * <p>双重检查：绝大多数请求走第一层判断（日期没变，直接返回），
     * 只有当天第一个请求会进入加锁区间执行 clear()。
     */
    private void rollIfNewDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDay)) {
            synchronized (this) {
                if (!today.equals(currentDay)) {
                    counters.clear();
                    currentDay = today;
                }
            }
        }
    }
}
