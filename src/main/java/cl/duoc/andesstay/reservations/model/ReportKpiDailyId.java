package cl.duoc.andesstay.reservations.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class ReportKpiDailyId implements Serializable {
    private LocalDate fecha;
    private Long unitId;

    public ReportKpiDailyId() {}
    public ReportKpiDailyId(LocalDate fecha, Long unitId) {
        this.fecha = fecha;
        this.unitId = unitId;
    }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public Long getUnitId() { return unitId; }
    public void setUnitId(Long unitId) { this.unitId = unitId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReportKpiDailyId that)) return false;
        return Objects.equals(fecha, that.fecha) && Objects.equals(unitId, that.unitId);
    }

    @Override
    public int hashCode() { return Objects.hash(fecha, unitId); }
}
