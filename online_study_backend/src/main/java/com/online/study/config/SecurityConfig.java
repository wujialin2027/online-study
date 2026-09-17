package com.online.study.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.online.study.common.Result;
import com.online.study.common.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring Security 6 配置
 *
 * <p>本类是「鉴权是否真的生效」的唯一开关。原代码最后一行写了
 * {@code anyRequest().permitAll()}，把上面所有的白名单、角色限制**全部作废**，
 * 等于整个系统没有任何鉴权。现在按下面的分级来。
 *
 * <h3>接口分级（从宽到严）</h3>
 * <ol>
 *   <li><b>完全公开</b>：{@code /auth/login}、{@code /auth/register} —— 没登录当然要能调</li>
 *   <li><b>静态资源公开</b>：{@code /uploads/**} —— 前端用 img/a 标签直接访问，
 *       浏览器不会带 Authorization 头</li>
 *   <li><b>CORS 预检放行</b>：{@code OPTIONS /**} —— 不放行的话跨域请求全部失败</li>
 *   <li><b>管理员专属</b>：{@code /admin/**} 需要 ROLE_ADMIN 权限</li>
 *   <li><b>其余全部</b>：必须登录（{@code authenticated()}）</li>
 * </ol>
 *
 * <p>注意 {@code hasAuthority("ROLE_ADMIN")} 之所以能生效，依赖于
 * {@link JwtAuthenticationFilter} 把 token 里的 role 真正写进了 authorities ——
 * 原代码那里传的是空集合，所以任何权限判断都无效。
 *
 * <p>401 / 403 由 Security 过滤器链直接返回，不经过 {@code GlobalExceptionHandler}
 * （因为异常发生在 DispatcherServlet 之前），所以在这里单独配了处理器，
 * 让它们也返回统一的 JSON 结构。
 */
@Configuration
@EnableWebSecurity
// 开启方法级权限控制，使接口上可以写 @PreAuthorize("hasRole('ADMIN')") 这类注解。
// 相比把规则全部堆在 filterChain 里，注解能精确到某一个接口（例如
// /course/audit 限管理员、/course-apply/apply 限学员），可读性更好。
@EnableMethodSecurity
public class SecurityConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // ① 公开：登录、注册
                    .requestMatchers("/auth/login", "/auth/register").permitAll()
                    // ② 公开：上传的课件、图片等静态资源（浏览器直接访问，不带 token）
                    .requestMatchers("/uploads/**").permitAll()
                    // ③ 必须放行：CORS 预检请求
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // ④ 管理员专属
                    .requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
                    // ⑤ 其余所有接口：必须登录
                    //    注意：这里**没有** anyRequest().permitAll() 兜底
                    .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, e) ->
                            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED))
                    .accessDeniedHandler((request, response, e) ->
                            writeJson(response, HttpServletResponse.SC_FORBIDDEN, ResultCode.FORBIDDEN))
            );

        // JWT 过滤器要在用户名密码过滤器之前执行
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 把统一返回结构写进响应体。
     * HTTP 状态码与 body 里的 code 保持一致，前端 axios 拦截器据此分流：
     * 401 → 清 token 并跳登录页；403 → 仅提示无权操作。
     */
    private void writeJson(HttpServletResponse response, int httpStatus, ResultCode resultCode) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(Result.failure(resultCode)));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 注意：setAllowedOrigins("*") 与 setAllowCredentials(true) 不能共存，
        // 同时设置会在运行时报错，必须用 setAllowedOriginPatterns
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
