package th.taxbook.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ล็อกอินด้วย Google (OpenID Connect) เก็บการล็อกอินในเซสชันฝั่งเซิร์ฟเวอร์ (คุกกี้ JSESSIONID แบบ HttpOnly)
 * หน้าเว็บแบบ static เปิดได้ทุกคน ส่วน /api/** ต้องล็อกอิน ถ้ายังไม่ล็อกอินตอบ 401 (ไม่ redirect)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookiePath("/");

        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/index.html", "/unsubscribe.html", "/privacy.html", "/terms.html", "/sitemap.xml", "/robots.txt", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/api/unsubscribe").permitAll()
                .requestMatchers("/admin/**").permitAll()      // ตรวจ X-Admin-Token ใน AdminController เอง
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated())
            .oauth2Login(o -> o.defaultSuccessUrl("/", true).failureUrl("/?login=error"))
            .logout(l -> l.logoutUrl("/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                .deleteCookies("JSESSIONID"))
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                (HttpServletRequest r) -> r.getRequestURI().startsWith("/api/")))
            // CSRF: หน้าเว็บอ่านคุกกี้ XSRF-TOKEN แล้วส่งกลับในหัวข้อ X-XSRF-TOKEN ทุกคำขอที่แก้ข้อมูล
            .csrf(c -> c
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                // ลิงก์ยกเลิกรับในอีเมล (One-Click ของ Gmail) และปลายทางทดสอบ ไม่มีเซสชัน จึงใช้โทเคนของตัวเองแทน
                .ignoringRequestMatchers("/api/unsubscribe", "/admin/**"))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .headers(h -> h
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(f -> f.deny())
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
        return http.build();
    }

    /** บังคับให้ออกคุกกี้ XSRF-TOKEN ตั้งแต่คำขอแรก (Spring Security 6 สร้างแบบขี้เกียจ) */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken();
            chain.doFilter(req, res);
        }
    }
}
