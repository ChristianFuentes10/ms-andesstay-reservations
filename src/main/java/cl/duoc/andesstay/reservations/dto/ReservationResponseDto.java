package cl.duoc.andesstay.reservations.dto;

import cl.duoc.andesstay.reservations.model.EstadoReserva;
import cl.duoc.andesstay.reservations.model.Reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ReservationResponseDto(
        Long id,
        Long unitId,
        String guestId,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer guestsCount,
        BigDecimal pricePerNight,
        BigDecimal totalPrice,
        EstadoReserva estado,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReservationResponseDto from(Reservation r) {
        return new ReservationResponseDto(
                r.getId(), r.getUnitId(), r.getGuestId(), r.getCheckIn(), r.getCheckOut(),
                r.getGuestsCount(), r.getPricePerNight(), r.getTotalPrice(), r.getEstado(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
