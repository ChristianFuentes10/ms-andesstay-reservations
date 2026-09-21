package cl.duoc.andesstay.reservations.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${andesstay.services.catalog.base-url}")
    private String catalogBaseUrl;

    /**
     * Cliente HTTP hacia ms-andesstay-catalog, usado por CatalogClient para
     * verificar que la unidad exista, este disponible y traer su
     * precio/noche vigente antes de crear una reserva.
     */
    @Bean
    public WebClient catalogWebClient() {
        return WebClient.builder()
                .baseUrl(catalogBaseUrl)
                .build();
    }
}
