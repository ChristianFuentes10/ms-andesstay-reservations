package cl.duoc.andesstay.reservations.exception;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(Long id) {
        super("No existe una reserva con id " + id);
    }
}
