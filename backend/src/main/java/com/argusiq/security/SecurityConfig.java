package com.argusiq.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            ArgusIqSecurityProperties properties,
            PasswordEncoder passwordEncoder
    ) {
        return new InMemoryUserDetailsManager(
                User.withUsername(properties.getInvestigationUsername())
                        .password(passwordEncoder.encode(properties.getInvestigationPassword()))
                        .roles("INVESTIGATE")
                        .build(),
                User.withUsername(properties.getIngestionUsername())
                        .password(passwordEncoder.encode(properties.getIngestionPassword()))
                        .roles("INGEST")
                        .build()
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers("/v1/traces", "/api/v1/otlp/**", "/ws/**")
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/traces", "/api/v1/otlp/v1/traces").hasRole("INGEST")
                        .requestMatchers("/ws/**", "/api/v1/**").hasRole("INVESTIGATE")
                        .anyRequest().denyAll()
                )
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }
}
