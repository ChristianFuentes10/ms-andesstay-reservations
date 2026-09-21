package cl.duoc.andesstay.reservations.repository;

import cl.duoc.andesstay.reservations.model.EstadoReserva;
import cl.duoc.andesstay.reservations.model.Reservation;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Igual que AuditEventSpecifications en ms-andesstay-audit: se arma el WHERE
 * con Criteria API agregando un predicado solo cuando el filtro viene con
 * valor, en vez de JPQL con "?/:param IS NULL OR columna = ?" (eso falla en
 * PostgreSQL porque no puede inferir el tipo del parametro en ese patron).
 */
public class ReservationSpecifications {

    private ReservationSpecifications() {
    }

    public static Specification<Reservation> conFiltros(String guestId, Long unitId,
                                                          EstadoReserva estado,
                                                          LocalDate desde, LocalDate hasta) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();

            if (guestId != null && !guestId.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("guestId"), guestId));
            }
            if (unitId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("unitId"), unitId));
            }
            if (estado != null) {
                predicates = cb.and(predicates, cb.equal(root.get("estado"), estado));
            }
            if (desde != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("checkIn"), desde));
            }
            if (hasta != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("checkOut"), hasta));
            }

            return predicates;
        };
    }
}
