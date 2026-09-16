package com.online.study.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * <p>注意：分页插件<b>必须注册这个 Bean 才生效</b>。
 * 只写 {@code Page<T> page = new Page<>(1, 10)} 而不注册插件的话，
 * MyBatis-Plus 不会真的分页，而是把全表查出来再在内存里截取——
 * 数据量大时是灾难，而且返回的 total 还是错的。
 * <p>这是「加了分页却仍然慢」最常见的原因。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // ① 分页插件
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页上限，防止前端传 size=999999 把库拖垮
        pagination.setMaxLimit(500L);
        // 请求页码超过总页数时，返回空列表而不是回到第一页（避免前端误以为还有数据）
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        // ② 防全表更新/删除插件
        // 拦截没有 where 条件的 update / delete，直接抛异常。
        // 这条对"手抖写了个 remove()"是真能救命的。
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}
