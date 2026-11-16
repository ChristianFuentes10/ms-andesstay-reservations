package cl.duoc.andesstay.reservations.dto;

import java.util.Map;

/** DTO con informacion de un topico Kafka. */
public record TopicInfoDto(
    String nombre,
    int particiones,
    int replicasFactor,
    boolean esInterno,
    Map<String, String> configuraciones
) {}
