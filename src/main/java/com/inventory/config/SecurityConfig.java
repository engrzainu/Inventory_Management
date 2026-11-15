package com.inventory.config;

import com.inventory.model.AppUser;
import com.inventory.repository.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppUserRepository users) throws Exception {
        LoginThrottle throttle = new LoginThrottle();
        http
            // CSRF protection: the token is kept in the XSRF-TOKEN cookie and the page JavaScript
            // sends it back in the X-XSRF-TOKEN header on every change request.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            // Re-checks the signed-in user against the database on every API call, so a disabled,
            // deleted or re-roled user loses (or gains) access immediately instead of at next login.
            .addFilterBefore(new FreshUserFilter(users), AuthorizationFilter.class)
            .addFilterBefore(new LoginThrottleFilter(throttle), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/login.html", "/css/**", "/js/**", "/icons/**", "/sw.js", "/manifest.json", "/favicon.ico", "/login").permitAll()
                .requestMatchers("/api/me").authenticated()
                // Accountant: sell products and view sales records (plus the business profile for receipts).
                .requestMatchers(HttpMethod.POST, "/api/sales").hasAnyRole("ADMIN", "ACCOUNTANT")
                .requestMatchers(HttpMethod.GET, "/api/sales", "/api/products/search", "/api/products/catalog", "/api/settings").hasAnyRole("ADMIN", "ACCOUNTANT")
                // Everything else is Inventory Admin only.
                .requestMatchers("/api/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    request -> request.getRequestURI().startsWith("/api/"))
                .accessDeniedHandler((request, response, denied) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"You do not have permission to do that.\"}");
                }))
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                .successHandler((request, response, authentication) -> {
                    throttle.succeeded(LoginThrottle.key(request));
                    response.setStatus(200);
                })
                .failureHandler((request, response, exception) -> {
                    throttle.failed(LoginThrottle.key(request));
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Invalid username or password\"}");
                })
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(200)));
        return http.build();
    }

    /** Blocks sign-in attempts after too many wrong passwords (see LoginThrottle). */
    static final class LoginThrottleFilter extends OncePerRequestFilter {
        private final LoginThrottle throttle;
        LoginThrottleFilter(LoginThrottle throttle) { this.throttle = throttle; }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !("POST".equals(request.getMethod()) && "/login".equals(request.getRequestURI()));
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            if (throttle.isLocked(LoginThrottle.key(request))) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many wrong passwords. Try again in 10 minutes.\"}");
                return;
            }
            chain.doFilter(request, response);
        }
    }

    /** Forces the deferred CSRF token to load so the cookie is always sent to the browser. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken();
            chain.doFilter(request, response);
        }
    }

    static final class FreshUserFilter extends OncePerRequestFilter {
        private final AppUserRepository users;
        FreshUserFilter(AppUserRepository users) { this.users = users; }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !request.getRequestURI().startsWith("/api/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                Optional<AppUser> user = users.findByUsernameIgnoreCase(auth.getName());
                if (user.isEmpty() || !user.get().isEnabled()) {
                    SecurityContextHolder.clearContext();
                    HttpSession session = request.getSession(false);
                    if (session != null) session.invalidate();
                } else {
                    String authority = "ROLE_" + user.get().getRole().name();
                    boolean same = auth.getAuthorities().size() == 1
                        && auth.getAuthorities().iterator().next().getAuthority().equals(authority);
                    if (!same) {
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            auth.getPrincipal(), auth.getCredentials(), AuthorityUtils.createAuthorityList(authority)));
                    }
                }
            }
            chain.doFilter(request, response);
        }
    }
}
