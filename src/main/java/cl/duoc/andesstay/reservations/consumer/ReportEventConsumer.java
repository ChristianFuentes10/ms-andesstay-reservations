package cl.duoc.andesstay.reservations.consumer;

import cl.duoc.andesstay.reservations.producer.ReportEventMessage;
import cl.duoc.andesstay.reservations.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Consume eventos del topico reporteria-eventos y delega en ReportService. */
@Component
public class ReportEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReportEventConsumer.class);

    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    public ReportEventConsumer(ReportService reportService, ObjectMapper objectMapper) {
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "${reporteria.kafka.topic:reporteria-eventos}",
        groupId = "${reporteria.kafka.group-id:report-generator}"
    )
    public void escuchar(
            @Payload String mensaje,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        log.debug("Evento recibido - topic={} partition={} offset={}", topic, partition, offset);
        try {
            ReportEventMessage evento = objectMapper.readValue(mensaje, ReportEventMessage.class);
            reportService.procesarEvento(evento);
        } catch (Exception e) {
            log.error("Error deserializando evento de reporteria en topic={} offset={}: {}",
                    topic, offset, e.getMessage(), e);
            throw new RuntimeException("Error procesando mensaje de reporteria", e);
        }
    }
}
