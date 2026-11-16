package cl.duoc.andesstay.reservations.controller;

import cl.duoc.andesstay.reservations.dto.*;
import cl.duoc.andesstay.reservations.model.ReportKpiDaily;
import cl.duoc.andesstay.reservations.repository.ReportEventLogRepository;
import cl.duoc.andesstay.reservations.repository.ReportKpiDailyRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API de reporteria (solo ADMIN). Lee de las tablas de KPIs pre-calculadas
 * por el consumidor Kafka para ofrecer reportes en tiempo real.
 */
@RestController
@RequestMapping("/api/reportes")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportKpiDailyRepository kpiRepo;
    private final ReportEventLogRepository eventLogRepo;

    public ReportController(ReportKpiDailyRepository kpiRepo,
                            ReportEventLogRepository eventLogRepo) {
        this.kpiRepo = kpiRepo;
        this.eventLogRepo = eventLogRepo;
    }

    /** Resumen total de KPIs: reservas por estado, ingresos, tasa de cancelacion, etc. */
    @GetMapping("/kpi/resumen")
    public ResponseEntity<KpiResumenDto> resumen() {
        Long creadas = kpiRepo.sumReservasCreadas();
        Long canceladas = kpiRepo.sumCanceladas();
        Long confirmadas = kpiRepo.sumConfirmadas();
        Long noches = kpiRepo.sumNochesReservadas();
        BigDecimal ingresos = kpiRepo.sumIngresosTotal();

        BigDecimal tasaCancelacion = BigDecimal.ZERO;
        if (creadas != null && creadas > 0) {
            tasaCancelacion = BigDecimal.valueOf(canceladas)
                    .divide(BigDecimal.valueOf(creadas), 4, RoundingMode.HALF_UP);
        }
        BigDecimal ticketPromedio = BigDecimal.ZERO;
        if (confirmadas != null && confirmadas > 0) {
            ticketPromedio = ingresos.divide(BigDecimal.valueOf(confirmadas), 2, RoundingMode.HALF_UP);
        }

        List<ReportKpiDaily> todos = kpiRepo.findAll();
        long checkins = todos.stream().mapToLong(k -> k.getCheckins() != null ? k.getCheckins() : 0).sum();
        long checkouts = todos.stream().mapToLong(k -> k.getCheckouts() != null ? k.getCheckouts() : 0).sum();

        return ResponseEntity.ok(new KpiResumenDto(
            creadas, confirmadas, checkins, checkouts, canceladas,
            ingresos, tasaCancelacion, noches, ticketPromedio
        ));
    }

    /** Serie diaria de KPIs entre dos fechas. */
    @GetMapping("/kpi/diario")
    public ResponseEntity<List<KpiDiarioDto>> serieDiaria(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        List<KpiDiarioDto> serie = kpiRepo.findByFechaBetweenOrderByFechaAsc(desde, hasta)
                .stream()
                .map(k -> new KpiDiarioDto(
                    k.getFecha(), k.getUnitId(), k.getReservasCreadas(), k.getConfirmadas(),
                    k.getCheckins(), k.getCheckouts(), k.getCanceladas(),
                    k.getIngresosConfirmados(), k.getNochesReservadas()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(serie);
    }

    /** Ranking de unidades por ingresos confirmados. */
    @GetMapping("/kpi/por-unidad")
    public ResponseEntity<List<KpiPorUnidadDto>> porUnidad() {
        // Agregar por unitId sumando todos los KPIs diarios
        var porUnidad = kpiRepo.findAllOrderByIngresosDesc()
                .stream()
                .collect(Collectors.groupingBy(k -> k.getUnitId()));

        List<KpiPorUnidadDto> resultado = porUnidad.entrySet().stream()
                .map(e -> {
                    Long unitId = e.getKey();
                    List<ReportKpiDaily> kpis = e.getValue();
                    long cr = kpis.stream().mapToLong(k -> k.getReservasCreadas() != null ? k.getReservasCreadas() : 0).sum();
                    long conf = kpis.stream().mapToLong(k -> k.getConfirmadas() != null ? k.getConfirmadas() : 0).sum();
                    long canc = kpis.stream().mapToLong(k -> k.getCanceladas() != null ? k.getCanceladas() : 0).sum();
                    BigDecimal ing = kpis.stream().map(k -> k.getIngresosConfirmados() != null ? k.getIngresosConfirmados() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long noches = kpis.stream().mapToLong(k -> k.getNochesReservadas() != null ? k.getNochesReservadas() : 0).sum();
                    BigDecimal tasa = cr > 0 ? BigDecimal.valueOf(canc).divide(BigDecimal.valueOf(cr), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    return new KpiPorUnidadDto(unitId, cr, conf, canc, ing, noches, tasa);
                })
                .sorted((a, b) -> b.ingresosConfirmados().compareTo(a.ingresosConfirmados()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(resultado);
    }

    /** Ultimos eventos procesados, paginado. */
    @GetMapping("/eventos")
    public ResponseEntity<List<ReportEventDto>> eventos(
            @RequestParam(required = false) String type) {
        var eventos = type != null
                ? eventLogRepo.findByTypeOrderByOccurredAtDesc(type)
                : eventLogRepo.findTop50ByOrderByOccurredAtDesc();
        List<ReportEventDto> dtos = eventos.stream()
                .map(e -> new ReportEventDto(
                    e.getEventId(), e.getReservationId(), e.getUnitId(), e.getGuestId(),
                    e.getType(), e.getEstado(), e.getGuestsCount(), e.getNights(),
                    e.getTotalPrice(), e.getOccurredAt(), e.getProcessedAt()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}
