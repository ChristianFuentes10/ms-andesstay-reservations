package cl.duoc.andesstay.reservations.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** KPI diario agregado por unidad. PK compuesta: fecha + unitId. */
@Entity
@Table(name = "report_kpi_daily")
@IdClass(ReportKpiDailyId.class)
public class ReportKpiDaily {

    @Id
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Id
    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "reservas_creadas", nullable = false)
    private Long reservasCreadas = 0L;

    @Column(name = "confirmadas", nullable = false)
    private Long confirmadas = 0L;

    @Column(name = "checkins", nullable = false)
    private Long checkins = 0L;

    @Column(name = "checkouts", nullable = false)
    private Long checkouts = 0L;

    @Column(name = "canceladas", nullable = false)
    private Long canceladas = 0L;

    /** Suma de totalPrice de eventos RESERVA_CONFIRMADA */
    @Column(name = "ingresos_confirmados", precision = 12, scale = 2)
    private BigDecimal ingresosConfirmados = BigDecimal.ZERO;

    /** Suma de noches de reservas creadas */
    @Column(name = "noches_reservadas", nullable = false)
    private Long nochesReservadas = 0L;

    public ReportKpiDaily() {}

    // Getters y setters
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public Long getUnitId() { return unitId; }
    public void setUnitId(Long unitId) { this.unitId = unitId; }
    public Long getReservasCreadas() { return reservasCreadas; }
    public void setReservasCreadas(Long reservasCreadas) { this.reservasCreadas = reservasCreadas; }
    public Long getConfirmadas() { return confirmadas; }
    public void setConfirmadas(Long confirmadas) { this.confirmadas = confirmadas; }
    public Long getCheckins() { return checkins; }
    public void setCheckins(Long checkins) { this.checkins = checkins; }
    public Long getCheckouts() { return checkouts; }
    public void setCheckouts(Long checkouts) { this.checkouts = checkouts; }
    public Long getCanceladas() { return canceladas; }
    public void setCanceladas(Long canceladas) { this.canceladas = canceladas; }
    public BigDecimal getIngresosConfirmados() { return ingresosConfirmados; }
    public void setIngresosConfirmados(BigDecimal ingresosConfirmados) { this.ingresosConfirmados = ingresosConfirmados; }
    public Long getNochesReservadas() { return nochesReservadas; }
    public void setNochesReservadas(Long nochesReservadas) { this.nochesReservadas = nochesReservadas; }
}
