package vn.glassliving.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.glassliving.auth.security.AppUserDetailsService;
import vn.glassliving.auth.security.JwtAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /** Disable Spring Boot's auto-registration of the JWT filter into the main servlet chain.
     *  We only want it inside our /api/** SecurityFilterChain. */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider();
        p.setUserDetailsService(userDetailsService);
        p.setPasswordEncoder(passwordEncoder());
        return p;
    }

    /** Stateless JWT chain for /api/** */
    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> {})
            // IF_REQUIRED so same-origin browser fetch from Thymeleaf pages reuses the web session.
            // JWT bearer/cookie still works for headless API clients.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(reg -> reg
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/verify-email",
                        "/api/v1/payments/vnpay/callback",
                        "/api/v1/payments/vnpay/ipn",
                        "/api/v1/payments/momo/callback").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/rooms",
                        "/api/v1/rooms/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasAnyRole("OWNER", "ADMIN", "STAFF")
                .anyRequest().authenticated())
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"success\":false,\"message\":\"Yêu cầu đăng nhập.\"}");
                }));
        return http.build();
    }

    /** Session-based chain for web (admin + customer Thymeleaf) */
    @Bean
    @Order(2)
    public SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        http
            .csrf(c -> {})
            .authorizeHttpRequests(reg -> reg
                .requestMatchers(
                        "/", "/rooms/**", "/login", "/register", "/forgot-password",
                        "/reset-password", "/verify-email",
                        "/css/**", "/js/**", "/img/**", "/fonts/**", "/favicon.ico",
                        "/h2-console/**",
                        "/actuator/health", "/actuator/info",
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/error").permitAll()
                .requestMatchers("/uploads/**").authenticated()
                .requestMatchers("/admin/**").hasAnyRole("OWNER", "ADMIN", "STAFF")
                .requestMatchers("/me/**", "/booking/**").authenticated()
                .anyRequest().permitAll())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/post-login", true)
                .failureUrl("/login?error=1")
                .permitAll())
            .logout(out -> out
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logout=1")
                .deleteCookies("JSESSIONID", "access_token")
                .permitAll())
            .rememberMe(rm -> rm.key("glass-living-remember-key").tokenValiditySeconds(60 * 60 * 24 * 30))
            .authenticationProvider(authenticationProvider())
            .headers(h -> h.frameOptions(f -> f.sameOrigin())); // for H2 console
        return http.build();
    }
}
