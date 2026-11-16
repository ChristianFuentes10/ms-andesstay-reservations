package cl.duoc.andesstay.reservations.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Registro de cada evento de reporteria procesado (idempotencia y trazabilidad). */
@Entity
@Table(name = "report_event_log", indexes = {
    @Index(name = "idx_rel_reservation_id", columnList = "reservation_id"),
    @Index(name = "idx_rel_occurred_at", columnList = "occurred_at")
})
public class ReportEventLog {

    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "reservation_id", nullable = false, length = 100)
    private String reservationId;

    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "guest_id", length = 100)
    private String guestId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "estado", length = 50)
    private String estado;

    @Column(name = "guests_count")
    private Integer guestsCount;

    @Column(name = "nights")
    private Long nights;

    @Column(name = "total_price", precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ReportEventLog() {}

    // Getters y setters
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public Long getUnitId() { return unitId; }
    public void setUnitId(Long unitId) { this.unitId = unitId; }
    public String getGuestId() { return guestId; }
    public void setGuestId(String guestId) { this.guestId = guestId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Integer getGuestsCount() { return guestsCount; }
    public void setGuestsCount(Integer guestsCount) { this.guestsCount = guestsCount; }
    public Long getNights() { return nights; }
    public void setNights(Long nights) { this.nights = nights; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
