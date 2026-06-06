package org.bank.moneytransfer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${moneytransfer.security.allow-anonymous:false}") boolean allowAnonymous) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers("/actuator/health", "/actuator/metrics", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
            if (allowAnonymous) {
                auth.anyRequest().permitAll();
            } else {
                auth.anyRequest().authenticated();
            }
        });
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder devJwtDecoder(@Value("${moneytransfer.security.allow-anonymous:false}") boolean allowAnonymous) {
        return token -> {
            if (allowAnonymous) {
                throw new JwtException("JWT decoding is not configured for local anonymous mode.");
            }
            throw new JwtException("Configure a JwtDecoder, issuer-uri, or jwk-set-uri for production.");
        };
    }
}
