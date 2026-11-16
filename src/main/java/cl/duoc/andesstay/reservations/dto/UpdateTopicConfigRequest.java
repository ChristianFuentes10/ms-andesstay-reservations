package cl.duoc.andesstay.reservations.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

/** Peticion para modificar configuraciones de un topico Kafka. */
public record UpdateTopicConfigRequest(
    @NotEmpty(message = "Debe especificar al menos una configuracion")
    Map<String, String> configs
) {}
