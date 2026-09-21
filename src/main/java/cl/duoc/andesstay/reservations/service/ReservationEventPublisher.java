package cl.duoc.andesstay.reservations.service;

import cl.duoc.andesstay.reservations.dto.ReservationEventMessage;
import cl.duoc.andesstay.reservations.model.Reservation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publica un evento por cada cambio de estado relevante de una reserva
 * (creada, confirmada, checkin, checkout, cancelada) en el topico
 * "reservations.events", que consume ms-andesstay-audit para trazabilidad.
 *
 * El value se manda como JSON en texto plano (StringSerializer, ver
 * application.properties) porque asi es como lo espera
 * AuditEventConsumer.escuchar(String) del lado consumidor.
 *
 * Deliberadamente NO se deja que una falla al publicar en Kafka tumbe la
 * operacion de negocio: si Kafka esta caido, la reserva igual se guarda en
 * Postgres (la fuente de verdad) y solo se pierde el evento de auditoria de
 * ese cambio puntual, en vez de bloquear al huesped/recepcionista.
 */
@Component
public class ReservationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${reservations.kafka.topic}")
    private String topic;

    public ReservationEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publicar(Reservation reservation, String actor) {
        ReservationEventMessage evento = new ReservationEventMessage(
                UUID.randomUUID().toString(),
                reservation.getEstado().tipoEvento(),
                Instant.now(),
                UUID.randomUUID().toString(),
                null,
                reservation.getId().toString(),
                actor
        );

        try {
            String payload = objectMapper.writeValueAsString(evento);
            // key = id de la reserva, para que todos los eventos de una
            // misma reserva queden en la misma particion y se procesen en orden.
            kafkaTemplate.send(topic, reservation.getId().toString(), payload);
            log.info("Evento publicado en '{}': tipo={}, reserva={}, actor={}",
                    topic, evento.type(), evento.reservationId(), actor);
        } catch (Exception e) {
            log.error("No se pudo publicar el evento de la reserva {} (estado {}) en Kafka. " +
                    "La reserva ya quedo guardada en la base de datos.",
                    reservation.getId(), reservation.getEstado(), e);
        }
    }
}
