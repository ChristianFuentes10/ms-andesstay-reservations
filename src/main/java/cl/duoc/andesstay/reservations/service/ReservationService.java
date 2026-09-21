package cl.duoc.andesstay.reservations.service;

import cl.duoc.andesstay.reservations.client.CatalogClient;
import cl.duoc.andesstay.reservations.dto.ReservationRequestDto;
import cl.duoc.andesstay.reservations.dto.UnitDto;
import cl.duoc.andesstay.reservations.exception.InvalidReservationStateException;
import cl.duoc.andesstay.reservations.exception.ReservationNotFoundException;
import cl.duoc.andesstay.reservations.exception.UnitNotAvailableException;
import cl.duoc.andesstay.reservations.model.EstadoReserva;
import cl.duoc.andesstay.reservations.model.Reservation;
import cl.duoc.andesstay.reservations.repository.ReservationRepository;
import cl.duoc.andesstay.reservations.repository.ReservationSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReservationService {

    // Estados que efectivamente bloquean una unidad para otras reservas.
    private static final List<EstadoReserva> ESTADOS_QUE_BLOQUEAN =
            List.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA, EstadoReserva.CHECKIN);

    private final ReservationRepository repository;
    private final CatalogClient catalogClient;
    private final ReservationEventPublisher eventPublisher;

    public ReservationService(ReservationRepository repository, CatalogClient catalogClient,
                               ReservationEventPublisher eventPublisher) {
        this.repository = repository;
        this.catalogClient = catalogClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Reservation crear(ReservationRequestDto request, String guestId) {
        if (!request.checkOut().isAfter(request.checkIn())) {
            throw new InvalidReservationStateException("La fecha de checkOut debe ser posterior a checkIn.");
        }

        UnitDto unit = catalogClient.findById(request.unitId())
                .orElseThrow(() -> new UnitNotAvailableException(
                        "No existe la unidad " + request.unitId() + " en el catalogo."));

        if (Boolean.FALSE.equals(unit.available())) {
            throw new UnitNotAvailableException("La unidad " + unit.id() + " no esta disponible.");
        }
        if (unit.capacity() != null && request.guestsCount() > unit.capacity()) {
            throw new UnitNotAvailableException(
                    "La unidad " + unit.id() + " tiene capacidad para " + unit.capacity()
                            + " huespedes, se solicitaron " + request.guestsCount() + ".");
        }

        List<Reservation> solapadas = repository
                .findByUnitIdAndEstadoInAndCheckInLessThanAndCheckOutGreaterThan(
                        request.unitId(), ESTADOS_QUE_BLOQUEAN, request.checkOut(), request.checkIn());
        if (!solapadas.isEmpty()) {
            throw new UnitNotAvailableException(
                    "La unidad " + unit.id() + " ya esta reservada en parte del rango solicitado.");
        }

        long noches = ChronoUnit.DAYS.between(request.checkIn(), request.checkOut());
        BigDecimal total = unit.pricePerNight().multiply(BigDecimal.valueOf(noches));

        Reservation reservation = new Reservation();
        reservation.setUnitId(request.unitId());
        reservation.setGuestId(guestId);
        reservation.setCheckIn(request.checkIn());
        reservation.setCheckOut(request.checkOut());
        reservation.setGuestsCount(request.guestsCount());
        reservation.setPricePerNight(unit.pricePerNight());
        reservation.setTotalPrice(total);
        reservation.setEstado(EstadoReserva.PENDIENTE);

        Reservation guardada = repository.save(reservation);
        eventPublisher.publicar(guardada, guestId);
        return guardada;
    }

    public Reservation buscarPorId(Long id) {
        return repository.findById(id).orElseThrow(() -> new ReservationNotFoundException(id));
    }

    /** Usado desde @PreAuthorize en el controller para saber si el usuario autenticado es el dueno de la reserva. */
    public boolean isOwner(Long id, String guestId) {
        return repository.findById(id)
                .map(r -> r.getGuestId().equals(guestId))
                .orElse(false);
    }

    public Page<Reservation> buscar(String guestId, Long unitId, EstadoReserva estado,
                                     LocalDate desde, LocalDate hasta, Pageable pageable) {
        var filtro = ReservationSpecifications.conFiltros(guestId, unitId, estado, desde, hasta);
        return repository.findAll(filtro, pageable);
    }

    public List<Reservation> buscarPorHuesped(String guestId) {
        return repository.findByGuestIdOrderByCreatedAtDesc(guestId);
    }

    @Transactional
    public Reservation confirmar(Long id, String actor) {
        Reservation reservation = buscarPorId(id);
        exigirEstado(reservation, EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA);
        reservation.setEstado(EstadoReserva.CONFIRMADA);
        eventPublisher.publicar(reservation, actor);
        return reservation;
    }

    @Transactional
    public Reservation checkIn(Long id, String actor) {
        Reservation reservation = buscarPorId(id);
        exigirEstado(reservation, EstadoReserva.CONFIRMADA, EstadoReserva.CHECKIN);
        reservation.setEstado(EstadoReserva.CHECKIN);
        eventPublisher.publicar(reservation, actor);
        return reservation;
    }

    @Transactional
    public Reservation checkOut(Long id, String actor) {
        Reservation reservation = buscarPorId(id);
        exigirEstado(reservation, EstadoReserva.CHECKIN, EstadoReserva.CHECKOUT);
        reservation.setEstado(EstadoReserva.CHECKOUT);
        eventPublisher.publicar(reservation, actor);
        return reservation;
    }

    @Transactional
    public Reservation cancelar(Long id, String actor) {
        Reservation reservation = buscarPorId(id);
        if (reservation.getEstado() != EstadoReserva.PENDIENTE
                && reservation.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new InvalidReservationStateException(
                    "Solo se puede cancelar una reserva PENDIENTE o CONFIRMADA (estado actual: "
                            + reservation.getEstado() + ").");
        }
        reservation.setEstado(EstadoReserva.CANCELADA);
        eventPublisher.publicar(reservation, actor);
        return reservation;
    }

    @Transactional
    public void eliminar(Long id) {
        repository.deleteById(id);
    }

    private void exigirEstado(Reservation reservation, EstadoReserva esperado, EstadoReserva siguiente) {
        if (reservation.getEstado() != esperado) {
            throw new InvalidReservationStateException(
                    "No se puede pasar la reserva a " + siguiente + " desde " + reservation.getEstado()
                            + " (se esperaba que estuviera en " + esperado + ").");
        }
    }
}
