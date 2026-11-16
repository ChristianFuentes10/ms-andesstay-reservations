package cl.duoc.andesstay.reservations.dto;

import java.math.BigDecimal;

/** DTO para el ranking de unidades por ingresos. */
public record KpiPorUnidadDto(
    Long unitId,
    Long reservasCreadas,
    Long confirmadas,
    Long canceladas,
    BigDecimal ingresosConfirmados,
    Long nochesReservadas,
    BigDecimal tasaCancelacion
) {}
