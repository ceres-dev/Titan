package dev.cerez.titan.infrastructure.config;

import dev.cerez.titan.log.Log;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/style/**", "/script/**", "/assets/**").permitAll()
                        .requestMatchers("/login", "/auth/login", "/about", "/robots.txt", "/").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/dashboard/**").authenticated()
                        .requestMatchers("/ws/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/auth/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .permitAll()
                        .successHandler((request, response, authentication) -> {
                            String username = authentication.getName();
                            String ip = request.getRemoteAddr() + "/" + request.getRemoteHost();
                            String userAgent = request.getHeader("User-Agent");

                            Log.info(
                                    "LOGIN_SUCCESS user=%S ip=%S userAgent=\"%s\"",
                                    username,
                                    ip,
                                    userAgent
                            );
                            response.sendRedirect("/dashboard");
                        })
                        .failureHandler((request, response, exception) -> {
                            String username = request.getParameter("username");
                            String ip = request.getRemoteAddr() + "/" + request.getRemoteHost();
                            String userAgent = request.getHeader("User-Agent");
                            Log.warning(
                                    "LOGIN_FAILURE user=%s ip=%s userAgent=%s reason=\"%s\"",
                                    username,
                                    ip,
                                    userAgent,
                                    exception.getClass().getSimpleName()
                            );
                            response.sendRedirect("/login?error");
                        })
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/login")
                );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}