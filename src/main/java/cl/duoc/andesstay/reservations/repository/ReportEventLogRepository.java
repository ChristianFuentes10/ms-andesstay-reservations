package cl.duoc.andesstay.reservations.repository;

import cl.duoc.andesstay.reservations.model.ReportEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportEventLogRepository extends JpaRepository<ReportEventLog, String> {

    /** Para idempotencia: saber si ya procesamos este evento. */
    boolean existsByEventId(String eventId);

    /** Ultimos N eventos ordenados por fecha de ocurrencia. */
    List<ReportEventLog> findTop50ByOrderByOccurredAtDesc();

    /** Eventos por tipo. */
    List<ReportEventLog> findByTypeOrderByOccurredAtDesc(String type);
}
