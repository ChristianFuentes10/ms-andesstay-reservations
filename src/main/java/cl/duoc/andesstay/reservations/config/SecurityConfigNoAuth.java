package cl.duoc.andesstay.reservations.config;

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
 * Configuracion de seguridad para desarrollo local (perfil nosec).
 * Deshabilita la autenticacion JWT para facilitar las pruebas con
 * curl o Postman sin necesidad de un token de Cognito.
 *
 * NUNCA usar este perfil en produccion o staging.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("nosec")
public class SecurityConfigNoAuth {

    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfigNoAuth(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChainNoAuth(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
