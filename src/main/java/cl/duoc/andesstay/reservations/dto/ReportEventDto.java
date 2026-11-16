package cl.duoc.andesstay.reservations.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** DTO para exponer eventos del log de reporteria (paginado). */
public record ReportEventDto(
    String eventId,
    String reservationId,
    Long unitId,
    String guestId,
    String type,
    String estado,
    Integer guestsCount,
    Long nights,
    BigDecimal totalPrice,
    Instant occurredAt,
    Instant processedAt
) {}
