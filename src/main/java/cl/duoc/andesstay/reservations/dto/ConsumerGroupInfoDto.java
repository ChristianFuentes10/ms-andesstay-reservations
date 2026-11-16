package cl.duoc.andesstay.reservations.dto;

/** DTO con informacion de un consumer group y su lag. */
public record ConsumerGroupInfoDto(
    String groupId,
    String estado,
    long lagTotal
) {}
