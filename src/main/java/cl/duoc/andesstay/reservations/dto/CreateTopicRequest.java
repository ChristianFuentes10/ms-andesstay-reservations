package cl.duoc.andesstay.reservations.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

/** Peticion para crear un topico Kafka. */
public record CreateTopicRequest(
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Nombre invalido: solo letras, numeros, puntos, guiones y subguiones")
    String name,
    @Min(value = 1, message = "Las particiones deben ser al menos 1")
    int partitions,
    @Min(value = 1, message = "El factor de replicacion debe ser al menos 1")
    int replicationFactor,
    Map<String, String> configs
) {}
