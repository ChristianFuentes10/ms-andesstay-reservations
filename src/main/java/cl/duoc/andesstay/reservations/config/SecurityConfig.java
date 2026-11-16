package cl.duoc.andesstay.reservations.config;

import cl.duoc.andesstay.reservations.security.CognitoJwtAuthenticationConverter;
import cl.duoc.andesstay.reservations.security.RestAccessDeniedHandler;
import cl.duoc.andesstay.reservations.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Configuracion de seguridad principal.
 *
 * Rutas publicas (sin autenticacion):
 *   - /actuator/health, /actuator/prometheus (para Prometheus scraping)
 *   - /swagger-ui.html, /api-docs/** (documentacion API)
 *
 * Rutas admin (requieren ROLE_ADMIN ademas del JWT valido):
 *   - /api/reportes/**, /api/admin/**
 *
 * Rutas autenticadas (cualquier JWT valido):
 *   - /api/reservations/** (control fino por rol en @PreAuthorize del controller)
 *
 * NOTA SEGURIDAD: /actuator/prometheus no debe exponerse publicamente en EC2.
 * Restriccion recomendada: Security Group que solo permita desde la IP de Prometheus.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!nosec")
public class SecurityConfig {

    private final CognitoJwtAuthenticationConverter cognitoJwtAuthenticationConverter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(CognitoJwtAuthenticationConverter cognitoJwtAuthenticationConverter,
                           RestAuthenticationEntryPoint authenticationEntryPoint,
                           RestAccessDeniedHandler accessDeniedHandler,
                           CorsConfigurationSource corsConfigurationSource) {
        this.cognitoJwtAuthenticationConverter = cognitoJwtAuthenticationConverter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Publicos: health, prometheus (Prometheus scraping), swagger
                        .requestMatchers(
                            "/actuator/health",
                            "/actuator/prometheus",
                            "/swagger-ui.html",
                            "/swagger-ui/**",
                            "/api-docs/**",
                            "/v3/api-docs/**"
                        ).permitAll()
                        // Admin exclusivo (refuerzo de capa URL ademas de @PreAuthorize)
                        .requestMatchers("/api/reportes/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Reservas: autenticado (control fino por rol en controller)
                        .requestMatchers("/api/reservations/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(cognitoJwtAuthenticationConverter)));

        return http.build();
    }
}
