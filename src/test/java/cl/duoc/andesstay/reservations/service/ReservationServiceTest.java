package cl.duoc.andesstay.reservations.service;

import cl.duoc.andesstay.reservations.client.CatalogClient;
import cl.duoc.andesstay.reservations.dto.ReservationRequestDto;
import cl.duoc.andesstay.reservations.dto.UnitDto;
import cl.duoc.andesstay.reservations.exception.InvalidReservationStateException;
import cl.duoc.andesstay.reservations.exception.UnitNotAvailableException;
import cl.duoc.andesstay.reservations.model.EstadoReserva;
import cl.duoc.andesstay.reservations.model.Reservation;
import cl.duoc.andesstay.reservations.producer.ReportEventProducer;
import cl.duoc.andesstay.reservations.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios del servicio de reservas: solapamiento, capacidad,
 * calculo de total y transiciones de estado invalidas.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository repository;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private ReservationEventPublisher eventPublisher;

    @Mock
    private ReportEventProducer reportEventProducer;

    @InjectMocks
    private ReservationService service;

    private UnitDto unitaDisponible;

    @BeforeEach
    void setup() {
        unitaDisponible = new UnitDto(1L, "Suite 101", "SUITE", 2, new BigDecimal("80000"), true);
    }

    @Test
    void crearReserva_calculaTotalCorrecamente() {
        // Arrange
        ReservationRequestDto request = new ReservationRequestDto(
            1L,
            LocalDate.of(2025, 1, 10),
            LocalDate.of(2025, 1, 15), // 5 noches
            2
        );
        when(catalogClient.findById(eq(1L), any())).thenReturn(Optional.of(unitaDisponible));
        when(repository.findByUnitIdAndEstadoInAndCheckInLessThanAndCheckOutGreaterThan(
            any(), any(), any(), any())).thenReturn(List.of());

        Reservation guardada = new Reservation();
        guardada.setId(1L);
        guardada.setUnitId(1L);
        guardada.setGuestId("guest-001");
        guardada.setCheckIn(request.checkIn());
        guardada.setCheckOut(request.checkOut());
        guardada.setGuestsCount(2);
        guardada.setPricePerNight(new BigDecimal("80000"));
        guardada.setTotalPrice(new BigDecimal("400000"));
        guardada.setEstado(EstadoReserva.PENDIENTE);
        when(repository.save(any())).thenReturn(guardada);

        // Act
        Reservation resultado = service.crear(request, "guest-001", "Bearer token");

        // Assert
        assertThat(resultado.getTotalPrice()).isEqualByComparingTo(new BigDecimal("400000"));
        assertThat(resultado.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        verify(eventPublisher).publicar(any(), eq("guest-001"));
        verify(reportEventProducer).publicar(any());
    }

    @Test
    void crearReserva_unidadSinCapacidad_lanzaExcepcion() {
        ReservationRequestDto request = new ReservationRequestDto(
            1L,
            LocalDate.of(2025, 1, 10),
            LocalDate.of(2025, 1, 15),
            5 // mas de la capacidad de 2
        );
        when(catalogClient.findById(eq(1L), any())).thenReturn(Optional.of(unitaDisponible));

        assertThatThrownBy(() -> service.crear(request, "guest-001", "Bearer token"))
            .isInstanceOf(UnitNotAvailableException.class)
            .hasMessageContaining("capacidad");
    }

    @Test
    void crearReserva_checkOutAntesDecheckIn_lanzaExcepcion() {
        ReservationRequestDto request = new ReservationRequestDto(
            1L,
            LocalDate.of(2025, 1, 15), // checkIn posterior
            LocalDate.of(2025, 1, 10), // checkOut anterior
            2
        );

        assertThatThrownBy(() -> service.crear(request, "guest-001", "Bearer token"))
            .isInstanceOf(InvalidReservationStateException.class)
            .hasMessageContaining("checkOut");
    }

    @Test
    void crearReserva_unidadConSolapamiento_lanzaExcepcion() {
        ReservationRequestDto request = new ReservationRequestDto(
            1L,
            LocalDate.of(2025, 1, 10),
            LocalDate.of(2025, 1, 15),
            2
        );
        when(catalogClient.findById(eq(1L), any())).thenReturn(Optional.of(unitaDisponible));

        Reservation existente = new Reservation();
        existente.setId(99L);
        when(repository.findByUnitIdAndEstadoInAndCheckInLessThanAndCheckOutGreaterThan(
            any(), any(), any(), any())).thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.crear(request, "guest-001", "Bearer token"))
            .isInstanceOf(UnitNotAvailableException.class)
            .hasMessageContaining("reservada");
    }

    @Test
    void confirmar_desdePendiente_cambiaaConfirmada() {
        Reservation r = new Reservation();
        r.setId(1L);
        r.setEstado(EstadoReserva.PENDIENTE);
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        Reservation resultado = service.confirmar(1L, "admin");

        assertThat(resultado.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    void confirmar_desdeCheckout_lanzaExcepcion() {
        Reservation r = new Reservation();
        r.setId(1L);
        r.setEstado(EstadoReserva.CHECKOUT);
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.confirmar(1L, "admin"))
            .isInstanceOf(InvalidReservationStateException.class);
    }

    @Test
    void cancelar_desdeCheckIn_lanzaExcepcion() {
        Reservation r = new Reservation();
        r.setId(1L);
        r.setEstado(EstadoReserva.CHECKIN);
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.cancelar(1L, "admin"))
            .isInstanceOf(InvalidReservationStateException.class)
            .hasMessageContaining("PENDIENTE o CONFIRMADA");
    }
}
