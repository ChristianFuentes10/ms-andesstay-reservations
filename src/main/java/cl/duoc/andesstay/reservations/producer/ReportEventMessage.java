package cl.duoc.andesstay.reservations.producer;

import java.math.BigDecimal;
import java.time.Instant;

/** Evento enriquecido para el topico de reporteria (reporteria-eventos). */
public record ReportEventMessage(
        String eventId,
        String type,
        Instant timestamp,
        String reservationId,
        Long unitId,
        String guestId,
        String estado,
        Integer guestsCount,
        Long nights,
        BigDecimal pricePerNight,
        BigDecimal totalPrice,
        String actor,
        String traceId
) {}
