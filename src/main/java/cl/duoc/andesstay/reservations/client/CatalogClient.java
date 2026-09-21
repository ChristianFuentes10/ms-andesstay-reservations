package cl.duoc.andesstay.reservations.client;

import cl.duoc.andesstay.reservations.dto.UnitDto;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

/**
 * Encapsula las llamadas HTTP hacia ms-andesstay-catalog, igual que
 * CatalogClient del BFF. Se usa .block() porque este microservicio es una
 * app servlet clasica (MVC): cada request ya corre en su propio hilo.
 */
@Component
public class CatalogClient {

    private final WebClient catalogWebClient;

    public CatalogClient(WebClient catalogWebClient) {
        this.catalogWebClient = catalogWebClient;
    }

    /**
     * Devuelve la unidad si existe, o Optional.empty() si el catalogo
     * responde 404. Cualquier otro error (catalogo caido, timeout, 5xx) se
     * deja propagar para que el GlobalExceptionHandler lo traduzca en 502.
     */
    public Optional<UnitDto> findById(Long unitId) {
        try {
            UnitDto unit = catalogWebClient.get()
                    .uri("/api/catalog/units/{id}", unitId)
                    .retrieve()
                    .bodyToMono(UnitDto.class)
                    .block();
            return Optional.ofNullable(unit);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        }
    }
}
