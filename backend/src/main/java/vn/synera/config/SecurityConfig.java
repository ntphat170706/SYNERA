package vn.synera.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import vn.synera.common.ApiResponse;
import vn.synera.iam.TokenUserFacade;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecretKeySpec jwtKey(@Value("${synera.jwt.secret}") String secret) {
    if (secret.length() < 32)
      throw new IllegalStateException(
          "JWT_SECRET must have at least 32 characters; configure a secret for production");
    return new SecretKeySpec(
        secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder encoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder decoder(SecretKeySpec key, @Value("${synera.jwt.issuer}") String issuer) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, TokenUserFacade users, ObjectMapper mapper)
      throws Exception {
    http.csrf(x -> x.disable())
        .cors(x -> {})
        .sessionManagement(x -> x.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.authorizeHttpRequests(
        x ->
            x.requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/otp/verify",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password",
                    "/api/v1/auth/session/login",
                    "/api/v1/auth/session/otp",
                    "/api/v1/auth/session/refresh",
                    "/api/v1/auth/session/logout",
                    "/api/v1/public/config",
                    "/internal/display-audits/*/result",
                    "/",
                    "/index.html",
                    "/assets/**",
                    "/icon.svg",
                    "/manifest.webmanifest",
                    "/sw.js",
                    "/robots.txt",
                    "/actuator/health",
                    "/actuator/health/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html")
                .permitAll()
                .requestMatchers("/actuator/**")
                .hasAuthority("USER_MANAGE")
                .anyRequest()
                .access(
                    (authentication, context) -> {
                      var auth = authentication.get();
                      boolean signed =
                          auth != null
                              && auth.isAuthenticated()
                              && !(auth
                                  instanceof
                                  org.springframework.security.authentication
                                      .AnonymousAuthenticationToken);
                      String path =
                          context
                              .getRequest()
                              .getRequestURI()
                              .substring(context.getRequest().getContextPath().length());
                      boolean driver =
                          auth != null
                              && auth.getAuthorities().stream()
                                  .anyMatch(a -> a.getAuthority().equals("ROLE_DRIVER"));
                      boolean store =
                          auth != null
                              && auth.getAuthorities().stream()
                                  .anyMatch(a -> a.getAuthority().equals("ROLE_STORE"));
                      boolean identity = path.startsWith("/api/v1/auth/");
                      boolean allowed =
                          signed
                              && (!driver
                                  || identity
                                  || path.startsWith("/api/v1/delivery-trips")
                                  || path.startsWith("/api/v1/attachments"))
                              && (!store || identity);
                      return new org.springframework.security.authorization.AuthorizationDecision(
                          allowed);
                    }));
    http.oauth2ResourceServer(
        x ->
            x.jwt(
                    j ->
                        j.jwtAuthenticationConverter(
                            jwt -> {
                              var u =
                                  users
                                      .findById(
                                          Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("w"))
                                      .filter(
                                          a ->
                                              a.enabled
                                                  && jwt.getClaim("v") instanceof Number n
                                                  && a.tokenVersion.longValue() == n.longValue())
                                      .orElseThrow(
                                          () ->
                                              new OAuth2AuthenticationException(
                                                  new OAuth2Error("invalid_token"),
                                                  "Session revoked"));
                              return new JwtAuthenticationToken(
                                  jwt,
                                  java.util.stream.Stream.concat(
                                          u.authorities.stream().map(SimpleGrantedAuthority::new),
                                          java.util.stream.Stream.of(
                                              new SimpleGrantedAuthority("ROLE_" + u.role)))
                                      .toList(),
                                  u.username);
                            }))
                .authenticationEntryPoint(
                    (req, res, e) -> {
                      res.setStatus(401);
                      res.setContentType("application/json");
                      mapper.writeValue(
                          res.getOutputStream(),
                          ApiResponse.fail(
                              "UNAUTHENTICATED", "Phiên hết hạn hoặc chưa đăng nhập.", Map.of()));
                    }));
    http.exceptionHandling(
        x ->
            x.accessDeniedHandler(
                (req, res, e) -> {
                  res.setStatus(403);
                  res.setContentType("application/json");
                  mapper.writeValue(
                      res.getOutputStream(),
                      ApiResponse.fail(
                          "FORBIDDEN_SCOPE", "Không có quyền thực hiện thao tác.", Map.of()));
                }));
    return http.build();
  }

  @Bean
  CorsConfigurationSource cors(@Value("${synera.cors-origins}") String origins) {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(Arrays.asList(origins.split(",")));
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
    c.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "Idempotency-Key",
            "X-Request-ID",
            "X-Session-Request"));
    c.setExposedHeaders(List.of("X-Request-ID"));
    c.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }
}
