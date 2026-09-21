package cl.duoc.andesstay.reservations.model;

/**
 * Estados posibles de una reserva y su transicion valida. El nombre de cada
 * estado se usa tal cual como "type" del evento publicado en Kafka (ver
 * ReservationEventPublisher), para que coincida con lo que
 * ms-andesstay-audit espera (RESERVA_CREADA, RESERVA_CONFIRMADA, etc).
 *
 * Maquina de estados:
 *   PENDIENTE -> CONFIRMADA -> CHECKIN -> CHECKOUT
 *   PENDIENTE -> CANCELADA
 *   CONFIRMADA -> CANCELADA
 */
public enum EstadoReserva {

    PENDIENTE("RESERVA_CREADA"),
    CONFIRMADA("RESERVA_CONFIRMADA"),
    CHECKIN("CHECKIN"),
    CHECKOUT("CHECKOUT"),
    CANCELADA("RESERVA_CANCELADA");

    private final String tipoEvento;

    EstadoReserva(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String tipoEvento() {
        return tipoEvento;
    }
}
