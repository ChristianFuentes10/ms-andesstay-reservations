package cl.duoc.andesstay.reservations.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTO para un punto de la serie diaria de KPIs. */
public record KpiDiarioDto(
    LocalDate fecha,
    Long unitId,
    Long reservasCreadas,
    Long confirmadas,
    Long checkins,
    Long checkouts,
    Long canceladas,
    BigDecimal ingresosConfirmados,
    Long nochesReservadas
) {}
