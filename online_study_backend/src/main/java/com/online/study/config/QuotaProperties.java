package com.online.study.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 演示环境的每日限额配置（对应 {@code application.yml} 的 {@code app.quota.*} 段）。
 *
 * <h3>为什么要有这一类配置</h3>
 * 这个项目部署在公网做演示，注册是开放的 —— 任何陌生人都能拿到一个学员账号。
 * 而有两类接口是「每次调用都真金白银花钱 / 占磁盘」的：
 * <pre>
 *   POST /ai/ask        每次提问都要调百炼的对话 + 向量接口（按 token 计费）
 *   POST /file/upload   每次上传都会往 2G 的服务器磁盘上落文件
 * </pre>
 * 不设限的话，一个脚本就能把余额刷干或把磁盘写满。所以按「账号 + 自然日」计数，
 * 到量就拒绝。
 *
 * <p><b>这里限不住什么</b>：进程内计数只认账号，挡不住"批量注册一千个账号"的玩法。
 * 那类攻击要靠 Nginx 按 IP 限流（见部署手册里的 {@code limit_req} 配置），
 * 两者是互补的：应用层管"每个账号能用多少"，网关层管"每个 IP 能敲多快"。
 *
 * <p>所有字段都带默认值，因此本地开发不用改 yml 也能跑起来；
 * 线上要调额度时才在服务器的 {@code application.yml} 里覆盖。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.quota")
public class QuotaProperties {

    /** 每个账号每天最多提问次数 */
    private int aiDaily = 30;

    /**
     * 演示账号每天最多提问次数。
     * <p>演示账号是多人共用（HR、面试官都拿它体验），正常额度很容易被卡住，
     * 所以单独放宽。
     */
    private int aiDailyDemo = 100;

    /** 演示账号的登录名（与 README、简历里公布的账号保持一致） */
    private String demoAccount = "student";

    /** 每个账号每天最多上传文件次数 */
    private int uploadDaily = 20;

    /**
     * 某个账号当天的提问额度：演示账号用放宽值，其余用普通值。
     * <p>写成方法而不是在 Controller 里写 if，是为了让"谁有多少额度"这件事
     * 只有一处定义。
     */
    public int askLimit(String account) {
        return account != null && account.equals(demoAccount) ? aiDailyDemo : aiDaily;
    }
}
