package cl.duoc.andesstay.reservations.controller;

import cl.duoc.andesstay.reservations.dto.ReservationRequestDto;
import cl.duoc.andesstay.reservations.dto.ReservationResponseDto;
import cl.duoc.andesstay.reservations.model.EstadoReserva;
import cl.duoc.andesstay.reservations.model.Reservation;
import cl.duoc.andesstay.reservations.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Puerta de entrada del dominio de reservas.
 *
 * Reglas de autorizacion (ver tambien SecurityConfig, que solo exige estar
 * autenticado a nivel de ruta; el detalle por rol/dueno vive aqui porque
 * depende del contenido de cada reserva):
 *   - HUESPED: crea sus propias reservas, ve y cancela solo las suyas.
 *   - RECEPCIONISTA: ve todas, confirma, hace checkin/checkout, cancela.
 *   - ADMIN: todo lo anterior, ademas puede eliminar (borrado fisico).
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'HUESPED')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponseDto crear(@Valid @RequestBody ReservationRequestDto request,
                                         @AuthenticationPrincipal Jwt jwt) {
        Reservation reservation = service.crear(request, actor(jwt));
        return ReservationResponseDto.from(reservation);
    }

    /** Listado paginado con filtros, para ADMIN/RECEPCIONISTA (vista operativa). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public Page<ReservationResponseDto> listar(
            @RequestParam(required = false) String guestId,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) EstadoReserva estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return service.buscar(guestId, unitId, estado, desde, hasta, pageable)
                .map(ReservationResponseDto::from);
    }

    /** Reservas del usuario autenticado. */
    @GetMapping("/mias")
    @PreAuthorize("isAuthenticated()")
    public List<ReservationResponseDto> misReservas(@AuthenticationPrincipal Jwt jwt) {
        return service.buscarPorHuesped(actor(jwt)).stream()
                .map(ReservationResponseDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA') or @reservationService.isOwner(#id, authentication.name)")
    public ReservationResponseDto obtener(@PathVariable Long id) {
        return ReservationResponseDto.from(service.buscarPorId(id));
    }

    @PutMapping("/{id}/confirmar")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public ReservationResponseDto confirmar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ReservationResponseDto.from(service.confirmar(id, actor(jwt)));
    }

    @PutMapping("/{id}/checkin")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public ReservationResponseDto checkIn(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ReservationResponseDto.from(service.checkIn(id, actor(jwt)));
    }

    @PutMapping("/{id}/checkout")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public ReservationResponseDto checkOut(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ReservationResponseDto.from(service.checkOut(id, actor(jwt)));
    }

    @PutMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA') or @reservationService.isOwner(#id, authentication.name)")
    public ReservationResponseDto cancelar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ReservationResponseDto.from(service.cancelar(id, actor(jwt)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.buscarPorId(id); // 404 homogeneo si no existe, antes de intentar borrar
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** sub del JWT: mismo identificador que usa CognitoJwtAuthenticationConverter como principal name. */
    private String actor(Jwt jwt) {
        return jwt.getSubject();
    }
}
