package cl.duoc.andesstay.reservations.dto;

import java.time.Instant;

/**
 * Envelope comun definido por el caso AndesStay para los eventos de negocio:
 * type, eventId, timestamp, traceId, correlationId - mas reservationId y
 * actor. Debe coincidir campo a campo con
 * cl.duoc.andesstay.audit.dto.ReservationEventMessage, que es quien lo
 * deserializa del lado consumidor (topico "reservations.events").
 */
public record ReservationEventMessage(
        String eventId,
        String type,
        Instant timestamp,
        String traceId,
        String correlationId,
        String reservationId,
        String actor
) {
}
