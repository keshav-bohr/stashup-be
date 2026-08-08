package com.stashup.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.stashup.common.config.ApplicationProperties;

/**
 * Constitution Principle IV: every endpoint is authenticated unless explicitly annotated as
 * public, and each public endpoint is justified.
 *
 * <p>The chain below is {@code anyRequest().authenticated()} — deny by default. The only
 * exceptions are:
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/register|login|refresh} — a caller cannot authenticate before
 *       obtaining a token, so these must be reachable unauthenticated. All three are rate-limited
 *       and the login path additionally applies per-account lockout.
 *   <li>{@code /actuator/health/**} — required by the container orchestrator before the
 *       application has any credentials to present. Exposes liveness only, never details.
 *   <li>OpenAPI documents — the published API contract is not secret.
 * </ul>
 */
@Configuration
public class SecurityConfig implements WebMvcConfigurer {

  private final ApplicationProperties properties;
  private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;

  public SecurityConfig(
      ApplicationProperties properties, CurrentUserIdArgumentResolver currentUserIdArgumentResolver) {
    this.properties = properties;
    this.currentUserIdArgumentResolver = currentUserIdArgumentResolver;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        // No browser session and no cookie-based auth, so there is no CSRF surface to protect.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST,
                "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
            .permitAll()
            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public JwtEncoder jwtEncoder() {
    return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withSecretKey(secretKey()).macAlgorithm(MacAlgorithm.HS256).build();
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(currentUserIdArgumentResolver);
  }

  private SecretKeySpec secretKey() {
    byte[] keyBytes = properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8);
    return new SecretKeySpec(keyBytes, "HmacSHA256");
  }
}
