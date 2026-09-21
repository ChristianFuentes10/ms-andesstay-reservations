package cl.duoc.andesstay.reservations.dto;

import java.math.BigDecimal;

/**
 * Copia local del contrato que expone ms-andesstay-catalog (mismo shape que
 * UnitDto del BFF). Se mantiene como DTO propio, no compartido como libreria,
 * a proposito: cada microservicio es independiente y solo depende del
 * contrato HTTP del catalogo, no de su codigo.
 */
public record UnitDto(
        Long id,
        String name,
        String type,
        Integer capacity,
        BigDecimal pricePerNight,
        Boolean available
) {
}
