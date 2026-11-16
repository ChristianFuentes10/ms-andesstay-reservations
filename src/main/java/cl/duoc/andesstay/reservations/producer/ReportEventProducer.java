package cl.duoc.andesstay.reservations.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publica eventos enriquecidos de reporteria en el topico reporteria-eventos. */
@Component
public class ReportEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ReportEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter exitoCounter;
    private final Counter falloCounter;

    @Value("${reporteria.kafka.topic:reporteria-eventos}")
    private String topic;

    public ReportEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper,
                                MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.exitoCounter = Counter.builder("andesstay_eventos_publicados_total")
                .tag("topic", "reporteria-eventos")
                .tag("resultado", "exito")
                .register(meterRegistry);
        this.falloCounter = Counter.builder("andesstay_eventos_publicados_total")
                .tag("topic", "reporteria-eventos")
                .tag("resultado", "fallo")
                .register(meterRegistry);
    }

    public void publicar(ReportEventMessage evento) {
        try {
            String payload = objectMapper.writeValueAsString(evento);
            kafkaTemplate.send(topic, evento.reservationId(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            exitoCounter.increment();
                            log.info("Evento reporteria publicado - topic={} partition={} offset={} tipo={} reserva={}",
                                    topic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset(),
                                    evento.type(),
                                    evento.reservationId());
                        } else {
                            falloCounter.increment();
                            log.error("Error al publicar evento reporteria en topic={} reserva={}: {}",
                                    topic, evento.reservationId(), ex.getMessage(), ex);
                        }
                    });
        } catch (Exception e) {
            falloCounter.increment();
            log.error("Error al serializar evento de reporteria para reserva={}: {}",
                    evento.reservationId(), e.getMessage(), e);
        }
    }
}
