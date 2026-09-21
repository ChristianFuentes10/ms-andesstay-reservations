package cl.duoc.andesstay.reservations.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SOLO PARA DESARROLLO LOCAL. Se activa con el perfil "nosec"
 * (spring.profiles.active=nosec), deja todo abierto para poder probar el
 * flujo completo (crear/confirmar/checkin/checkout/cancelar + publicacion
 * en Kafka) antes de tener el User Pool de Cognito creado.
 *
 * NUNCA usar este perfil en la demo/presentacion real ni en produccion.
 */
@Configuration
@EnableWebSecurity
@Profile("nosec")
public class SecurityConfigNoAuth {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
