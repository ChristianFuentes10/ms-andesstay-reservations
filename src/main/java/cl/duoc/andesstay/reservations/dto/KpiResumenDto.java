package cl.duoc.andesstay.reservations.dto;

import java.math.BigDecimal;

/** DTO con el resumen total de KPIs para el dashboard. */
public record KpiResumenDto(
    Long reservasCreadas,
    Long confirmadas,
    Long checkins,
    Long checkouts,
    Long canceladas,
    BigDecimal ingresosTotal,
    BigDecimal tasaCancelacion,
    Long nochesReservadas,
    BigDecimal ticketPromedio
) {}
