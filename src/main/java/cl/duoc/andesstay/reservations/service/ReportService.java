package cl.duoc.andesstay.reservations.service;

import cl.duoc.andesstay.reservations.model.ReportEventLog;
import cl.duoc.andesstay.reservations.model.ReportKpiDaily;
import cl.duoc.andesstay.reservations.model.ReportKpiDailyId;
import cl.duoc.andesstay.reservations.producer.ReportEventMessage;
import cl.duoc.andesstay.reservations.repository.ReportEventLogRepository;
import cl.duoc.andesstay.reservations.repository.ReportKpiDailyRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Procesa eventos de reporteria: registra en report_event_log y actualiza
 * los KPIs diarios en report_kpi_daily usando upsert dentro de la misma
 * transaccion.
 *
 * Reglas de KPI:
 * - reservas_creadas: eventos RESERVA_CREADA
 * - confirmadas: eventos RESERVA_CONFIRMADA
 * - checkins: eventos CHECKIN
 * - checkouts: eventos CHECKOUT
 * - canceladas: eventos RESERVA_CANCELADA
 * - ingresos_confirmados: suma de totalPrice en eventos RESERVA_CONFIRMADA
 * - noches_reservadas: suma de nights en eventos RESERVA_CREADA
 * - tasa_cancelacion = canceladas / reservas_creadas (calculado en API)
 * - ticket_promedio = ingresos_confirmados / confirmadas (calculado en API)
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportEventLogRepository eventLogRepo;
    private final ReportKpiDailyRepository kpiRepo;
    private final Timer processingTimer;
    private final Counter consumidosExitoCounter;
    private final Counter consumidosFalloCounter;

    public ReportService(ReportEventLogRepository eventLogRepo,
                         ReportKpiDailyRepository kpiRepo,
                         MeterRegistry meterRegistry) {
        this.eventLogRepo = eventLogRepo;
        this.kpiRepo = kpiRepo;
        this.processingTimer = Timer.builder("andesstay_reporte_procesamiento_seconds")
                .description("Tiempo de procesamiento de un evento de reporteria")
                .register(meterRegistry);
        this.consumidosExitoCounter = Counter.builder("andesstay_eventos_consumidos_total")
                .tag("resultado", "exito")
                .register(meterRegistry);
        this.consumidosFalloCounter = Counter.builder("andesstay_eventos_consumidos_total")
                .tag("resultado", "fallo")
                .register(meterRegistry);
    }

    @Transactional
    public void procesarEvento(ReportEventMessage evento) {
        processingTimer.record(() -> {
            try {
                // Idempotencia: si ya procesamos este eventId, ignorar
                if (eventLogRepo.existsByEventId(evento.eventId())) {
                    log.warn("Evento duplicado ignorado: eventId={} reserva={}",
                            evento.eventId(), evento.reservationId());
                    return;
                }

                // 1. Registrar en log
                ReportEventLog logEntry = new ReportEventLog();
                logEntry.setEventId(evento.eventId());
                logEntry.setReservationId(evento.reservationId());
                logEntry.setUnitId(evento.unitId());
                logEntry.setGuestId(evento.guestId());
                logEntry.setType(evento.type());
                logEntry.setEstado(evento.estado());
                logEntry.setGuestsCount(evento.guestsCount());
                logEntry.setNights(evento.nights());
                logEntry.setTotalPrice(evento.totalPrice());
                logEntry.setOccurredAt(evento.timestamp());
                logEntry.setProcessedAt(Instant.now());
                eventLogRepo.save(logEntry);

                // 2. Actualizar KPI diario (upsert)
                if (evento.unitId() != null) {
                    LocalDate fecha = evento.timestamp().atZone(ZoneOffset.UTC).toLocalDate();
                    ReportKpiDailyId kpiId = new ReportKpiDailyId(fecha, evento.unitId());
                    ReportKpiDaily kpi = kpiRepo.findById(kpiId).orElseGet(() -> {
                        ReportKpiDaily nuevo = new ReportKpiDaily();
                        nuevo.setFecha(fecha);
                        nuevo.setUnitId(evento.unitId());
                        return nuevo;
                    });

                    switch (evento.type()) {
                        case "RESERVA_CREADA" -> {
                            kpi.setReservasCreadas(kpi.getReservasCreadas() + 1);
                            if (evento.nights() != null) {
                                kpi.setNochesReservadas(kpi.getNochesReservadas() + evento.nights());
                            }
                        }
                        case "RESERVA_CONFIRMADA" -> {
                            kpi.setConfirmadas(kpi.getConfirmadas() + 1);
                            if (evento.totalPrice() != null) {
                                kpi.setIngresosConfirmados(
                                    kpi.getIngresosConfirmados().add(evento.totalPrice()));
                            }
                        }
                        case "CHECKIN" -> kpi.setCheckins(kpi.getCheckins() + 1);
                        case "CHECKOUT" -> kpi.setCheckouts(kpi.getCheckouts() + 1);
                        case "RESERVA_CANCELADA" -> kpi.setCanceladas(kpi.getCanceladas() + 1);
                        default -> log.debug("Tipo de evento no clasificado para KPI: {}", evento.type());
                    }

                    kpiRepo.save(kpi);
                }

                consumidosExitoCounter.increment();
                log.info("Evento de reporteria procesado: eventId={} tipo={} reserva={}",
                        evento.eventId(), evento.type(), evento.reservationId());
            } catch (Exception e) {
                consumidosFalloCounter.increment();
                log.error("Error procesando evento de reporteria eventId={}: {}",
                        evento.eventId(), e.getMessage(), e);
                throw e; // Relanzar para que el ErrorHandler de Kafka lo maneje
            }
        });
    }

    @Transactional
    public void limpiarDatosDemo() {
        // Elimina solo los eventos marcados con prefijo demo-
        var demos = eventLogRepo.findAll().stream()
                .filter(e -> e.getEventId().startsWith("demo-"))
                .toList();
        eventLogRepo.deleteAll(demos);
        log.info("Datos demo eliminados: {} eventos", demos.size());
    }
}
