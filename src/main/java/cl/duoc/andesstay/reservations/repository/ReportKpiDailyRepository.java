package cl.duoc.andesstay.reservations.repository;

import cl.duoc.andesstay.reservations.model.ReportKpiDaily;
import cl.duoc.andesstay.reservations.model.ReportKpiDailyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReportKpiDailyRepository extends JpaRepository<ReportKpiDaily, ReportKpiDailyId> {

    /** KPI de un rango de fechas (serie diaria). */
    List<ReportKpiDaily> findByFechaBetweenOrderByFechaAsc(LocalDate desde, LocalDate hasta);

    /** Totales por unidad (ranking). */
    @Query("SELECT k FROM ReportKpiDaily k ORDER BY k.ingresosConfirmados DESC")
    List<ReportKpiDaily> findAllOrderByIngresosDesc();

    /** Suma de ingresos totales. */
    @Query("SELECT COALESCE(SUM(k.ingresosConfirmados), 0) FROM ReportKpiDaily k")
    java.math.BigDecimal sumIngresosTotal();

    /** Suma de reservas creadas (para tasa de cancelacion). */
    @Query("SELECT COALESCE(SUM(k.reservasCreadas), 0) FROM ReportKpiDaily k")
    Long sumReservasCreadas();

    /** Suma de canceladas. */
    @Query("SELECT COALESCE(SUM(k.canceladas), 0) FROM ReportKpiDaily k")
    Long sumCanceladas();

    /** Total noches reservadas. */
    @Query("SELECT COALESCE(SUM(k.nochesReservadas), 0) FROM ReportKpiDaily k")
    Long sumNochesReservadas();

    /** Total confirmadas. */
    @Query("SELECT COALESCE(SUM(k.confirmadas), 0) FROM ReportKpiDaily k")
    Long sumConfirmadas();
}
