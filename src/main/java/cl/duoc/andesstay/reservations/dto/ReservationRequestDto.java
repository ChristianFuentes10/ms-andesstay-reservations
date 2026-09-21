package cl.duoc.andesstay.reservations.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * El campo guestId NO viene en el request: la reserva siempre se crea para
 * el usuario autenticado (claim "sub"/"username" del JWT), asi un huesped
 * no puede reservar "a nombre de otro" armando el body a mano - ver
 * ReservationController.
 */
public record ReservationRequestDto(
        @NotNull Long unitId,
        @NotNull @FutureOrPresent LocalDate checkIn,
        @NotNull @FutureOrPresent LocalDate checkOut,
        @NotNull @Positive Integer guestsCount
) {
}
