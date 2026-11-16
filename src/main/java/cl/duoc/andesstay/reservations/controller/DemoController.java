package cl.duoc.andesstay.reservations.controller;

import cl.duoc.andesstay.reservations.producer.ReportEventMessage;
import cl.duoc.andesstay.reservations.producer.ReportEventProducer;
import cl.duoc.andesstay.reservations.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Endpoints para generacion de datos de demo (solo con DEMO_ENABLED=true).
 * Genera eventos sinteticos realistas para mostrar los dashboards de Grafana
 * en la presentacion de 10 minutos.
 */
@RestController
@RequestMapping("/api/admin/demo")
@PreAuthorize("hasRole('ADMIN')")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final ReportEventProducer reportEventProducer;
    private final ReportService reportService;

    @Value("${demo.enabled:false}")
    private boolean demoEnabled;

    private static final List<String> ESTADOS = List.of(
        "RESERVA_CREADA", "RESERVA_CONFIRMADA", "RESERVA_CHECKIN",
        "RESERVA_CHECKOUT", "RESERVA_CANCELADA"
    );
    private static final long[] UNIT_IDS = {1L, 2L, 3L, 4L, 5L};
    private static final String[] GUEST_IDS = {
        "guest-001", "guest-002", "guest-003", "guest-004", "guest-005"
    };

    public DemoController(ReportEventProducer reportEventProducer, ReportService reportService) {
        this.reportEventProducer = reportEventProducer;
        this.reportService = reportService;
    }

    /**
     * Genera N eventos sinteticos y los publica en el topico reporteria-eventos.
     * Solo disponible si demo.enabled=true.
     */
    @PostMapping("/generar")
    public ResponseEntity<Map<String, Object>> generarDatos(
            @RequestParam(defaultValue = "50") int cantidad) {
        if (!demoEnabled) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Demo no habilitado. Configure DEMO_ENABLED=true"));
        }

        Random random = new Random();
        int generados = 0;

        for (int i = 0; i < cantidad; i++) {
            String reservationId = "demo-reserva-" + (i + 1);
            Long unitId = UNIT_IDS[random.nextInt(UNIT_IDS.length)];
            String guestId = GUEST_IDS[random.nextInt(GUEST_IDS.length)];
            String tipo = ESTADOS.get(random.nextInt(ESTADOS.size()));

            // Fecha aleatoria en los ultimos 30 dias
            long diasAtras = random.nextLong(30);
            Instant timestamp = Instant.now().minus(diasAtras, ChronoUnit.DAYS);

            long noches = 1 + random.nextLong(14);
            BigDecimal precioPorNoche = BigDecimal.valueOf(50_000 + random.nextInt(150_000));
            BigDecimal totalPrice = precioPorNoche.multiply(BigDecimal.valueOf(noches));

            String estado = switch (tipo) {
                case "RESERVA_CREADA" -> "PENDIENTE";
                case "RESERVA_CONFIRMADA" -> "CONFIRMADA";
                case "RESERVA_CHECKIN" -> "CHECKIN";
                case "RESERVA_CHECKOUT" -> "CHECKOUT";
                case "RESERVA_CANCELADA" -> "CANCELADA";
                default -> "PENDIENTE";
            };

            ReportEventMessage evento = new ReportEventMessage(
                "demo-" + UUID.randomUUID(),
                tipo,
                timestamp,
                reservationId,
                unitId,
                guestId,
                estado,
                1 + random.nextInt(4),
                noches,
                precioPorNoche,
                totalPrice,
                "sistema-demo",
                UUID.randomUUID().toString()
            );

            reportEventProducer.publicar(evento);
            generados++;
        }

        log.info("Demo: {} eventos sinteticos publicados en reporteria-eventos", generados);
        return ResponseEntity.ok(Map.of(
            "mensaje", "Eventos demo publicados exitosamente",
            "cantidad", generados,
            "topico", "reporteria-eventos"
        ));
    }

    /** Elimina los datos de demo (eventId con prefijo demo-). */
    @DeleteMapping("/limpiar")
    public ResponseEntity<Map<String, String>> limpiarDatos() {
        if (!demoEnabled) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Demo no habilitado. Configure DEMO_ENABLED=true"));
        }
        reportService.limpiarDatosDemo();
        return ResponseEntity.ok(Map.of("mensaje", "Datos de demo eliminados exitosamente"));
    }
}
