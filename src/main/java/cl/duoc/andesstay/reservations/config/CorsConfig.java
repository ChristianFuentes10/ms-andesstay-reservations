package cl.duoc.andesstay.reservations.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuracion de CORS para permitir que el frontend Angular (puerto 4200)
 * consuma la API con el header Authorization (JWT de Cognito).
 *
 * Los origenes permitidos se configuran con la variable CORS_ALLOWED_ORIGINS
 * (separados por coma si son varios).
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String allowedOriginsRaw;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Parsear origenes separados por coma
        List<String> origenes = Arrays.asList(allowedOriginsRaw.split(","));
        config.setAllowedOrigins(origenes);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "X-Trace-Id", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization", "X-Trace-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
