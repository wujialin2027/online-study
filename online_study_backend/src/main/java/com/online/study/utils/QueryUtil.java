package com.online.study.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 查询条件工具类
 *
 * <h3>解决什么问题</h3>
 * 原项目所有 Controller 的 {@code /query} 接口都是这样写的：
 * <pre>
 * params.forEach((k, v) -&gt; {
 *     String column = k.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
 *     wrapper.eq(column, v);       // ← 列名直接来自前端，没有任何校验
 * });
 * </pre>
 * 参数<b>值</b>虽然是预编译的（安全），但<b>列名是拼进 SQL 的</b>。
 * 攻击者构造一个恶意的 key，就能改变 SQL 语句结构 —— 这就是 SQL 注入。
 *
 * <h3>怎么修的</h3>
 * 不再相信前端传的 key，而是用<b>反射</b>取出实体类里真实存在的字段，
 * 生成一份「列名白名单」。只有白名单里的 key 才会被拼进查询条件，
 * 其余一律丢弃并记一条 warn 日志。
 *
 * <p>副作用：前端如果拼错了字段名，原来会报 SQL 错误，现在会被静默忽略。
 * 所以保留了 warn 日志，排查问题时能从日志里看到。
 *
 * <p>白名单结果按实体类缓存，避免每次请求都做反射（反射有性能开销）。
 */
public final class QueryUtil {

    private static final Logger log = LoggerFactory.getLogger(QueryUtil.class);

    /** 实体类 → 允许作为查询条件的列名集合 */
    private static final Map<Class<?>, Set<String>> COLUMN_CACHE = new ConcurrentHashMap<>();

    private QueryUtil() {
        // 工具类不允许实例化
    }

    /**
     * 把前端传来的查询条件安全地转换为 QueryWrapper。
     *
     * @param entityClass 实体类型，用于推导合法列名白名单
     * @param params      前端传来的条件（key 为实体字段名，驼峰）：{@code {"courseName": "Java"}}
     * @return 只包含合法条件的 QueryWrapper（可能为空条件，即查全部）
     */
    public static <T> QueryWrapper<T> buildSafeWrapper(Class<T> entityClass, Map<String, Object> params) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        if (params == null || params.isEmpty()) {
            return wrapper;
        }
        Set<String> allowedColumns = COLUMN_CACHE.computeIfAbsent(entityClass, QueryUtil::resolveColumns);

        params.forEach((key, value) -> {
            if (key == null || value == null || "".equals(value.toString())) {
                return;
            }
            String column = camelToUnderline(key);
            if (!allowedColumns.contains(column)) {
                // 不在白名单：可能是前端拼错字段，也可能是恶意构造，统一忽略
                log.warn("查询条件中存在非法字段，已忽略：{}（实体：{}）", key, entityClass.getSimpleName());
                return;
            }
            wrapper.eq(column, value);
        });
        return wrapper;
    }

    /** 反射收集实体类所有实例字段，转成数据库列名集合 */
    private static Set<String> resolveColumns(Class<?> entityClass) {
        return Arrays.stream(entityClass.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .map(QueryUtil::camelToUnderline)
                .collect(Collectors.toSet());
    }

    /** 驼峰转下划线：{@code courseName} → {@code course_name} */
    private static String camelToUnderline(String name) {
        return name.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }
}
