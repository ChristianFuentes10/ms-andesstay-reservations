package cl.duoc.andesstay.reservations.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${andesstay.services.catalog.base-url}")
    private String catalogBaseUrl;

    @Bean
    public WebClient catalogWebClient() {
        return WebClient.builder()
                .baseUrl(catalogBaseUrl)
                .filter(forwardJwt())
                .build();
    }

    private ExchangeFilterFunction forwardJwt() {
        return (request, next) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                ClientRequest autorizado = ClientRequest.from(request)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAuth.getToken().getTokenValue())
                        .build();
                return next.exchange(autorizado);
            }
            return next.exchange(request);
        };
    }
}