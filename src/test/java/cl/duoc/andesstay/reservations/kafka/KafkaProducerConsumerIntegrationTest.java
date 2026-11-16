package cl.duoc.andesstay.reservations.kafka;

import cl.duoc.andesstay.reservations.consumer.ReportEventConsumer;
import cl.duoc.andesstay.reservations.producer.ReportEventMessage;
import cl.duoc.andesstay.reservations.producer.ReportEventProducer;
import cl.duoc.andesstay.reservations.repository.ReportEventLogRepository;
import cl.duoc.andesstay.reservations.repository.ReportKpiDailyRepository;
import cl.duoc.andesstay.reservations.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test de integracion del flujo productor -> Kafka -> consumidor con Kafka embebido.
 * Verifica que un evento publicado en reporteria-eventos sea procesado por el consumidor
 * y que la idempotencia funcione correctamente.
 */
@SpringBootTest
@ActiveProfiles("nosec")
@EmbeddedKafka(
    partitions = 3,
    topics = {"reporteria-eventos", "reporteria-eventos.DLT"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:${random.int[10000,20000]}",
        "auto.create.topics.enable=true"
    }
)
@DirtiesContext
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.datasource.url=jdbc:h2:mem:testdb_kafka;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "reporteria.kafka.topic=reporteria-eventos",
    "reporteria.kafka.group-id=report-generator-test",
    "spring.kafka.consumer.group-id=report-generator-test",
    "andesstay.services.catalog.base-url=http://localhost:9999"
})
class KafkaProducerConsumerIntegrationTest {

    @Autowired
    private ReportEventProducer reportEventProducer;

    @Autowired
    private ReportEventLogRepository eventLogRepo;

    @Autowired
    private ReportKpiDailyRepository kpiRepo;

    @Test
    void publicarEvento_consumidorActualizaKpiDaily() throws Exception {
        // Arrange
        String reservationId = "res-" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        ReportEventMessage evento = new ReportEventMessage(
            eventId,
            "RESERVA_CREADA",
            Instant.now(),
            reservationId,
            1L,
            "guest-001",
            "PENDIENTE",
            2,
            5L,
            new BigDecimal("80000"),
            new BigDecimal("400000"),
            "test-actor",
            UUID.randomUUID().toString()
        );

        // Act
        reportEventProducer.publicar(evento);

        // Assert: esperar hasta 10 segundos a que el consumidor procese el evento
        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                assert eventLogRepo.existsByEventId(eventId) : "El evento no fue registrado en report_event_log";
            });
    }

    @Test
    void idempotencia_mismoeventIdNoSeProcesaDosVeces() throws Exception {
        // Arrange
        String reservationId = "res-idem-" + UUID.randomUUID();
        String eventId = "idem-" + UUID.randomUUID();

        ReportEventMessage evento = new ReportEventMessage(
            eventId,
            "RESERVA_CONFIRMADA",
            Instant.now(),
            reservationId,
            2L,
            "guest-002",
            "CONFIRMADA",
            1,
            3L,
            new BigDecimal("100000"),
            new BigDecimal("300000"),
            "test-actor",
            UUID.randomUUID().toString()
        );

        // Publicar el mismo evento dos veces
        reportEventProducer.publicar(evento);
        Thread.sleep(500);
        reportEventProducer.publicar(new ReportEventMessage(
            eventId, // mismo eventId!
            "RESERVA_CONFIRMADA",
            Instant.now(),
            reservationId,
            2L,
            "guest-002",
            "CONFIRMADA",
            1,
            3L,
            new BigDecimal("100000"),
            new BigDecimal("300000"),
            "test-actor-dup",
            UUID.randomUUID().toString()
        ));

        // Assert: debe existir exactamente un registro con ese eventId
        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                assert eventLogRepo.existsByEventId(eventId) : "El evento debe haberse registrado";
                long count = eventLogRepo.findAll().stream()
                    .filter(e -> eventId.equals(e.getEventId()))
                    .count();
                assert count == 1 : "El evento duplicado no debio procesarse (count=" + count + ")";
            });
    }
}
