package cl.duoc.andesstay.reservations.repository;

import cl.duoc.andesstay.reservations.model.EstadoReserva;
import cl.duoc.andesstay.reservations.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long>,
        JpaSpecificationExecutor<Reservation> {

    List<Reservation> findByGuestIdOrderByCreatedAtDesc(String guestId);

    /**
     * Reservas de una unidad que se solapan con el rango [checkIn, checkOut)
     * y que estan en un estado que efectivamente bloquea la unidad
     * (PENDIENTE, CONFIRMADA o CHECKIN). Se usa para no permitir doble
     * reserva de la misma unidad en fechas cruzadas.
     */
    List<Reservation> findByUnitIdAndEstadoInAndCheckInLessThanAndCheckOutGreaterThan(
            Long unitId, List<EstadoReserva> estados, LocalDate checkOut, LocalDate checkIn);
}
