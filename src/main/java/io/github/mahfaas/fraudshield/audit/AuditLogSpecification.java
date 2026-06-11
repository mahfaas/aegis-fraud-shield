package io.github.mahfaas.fraudshield.audit;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Data JPA Specification for dynamic, multi-field querying of the audit log.
 *
 * <p>Demonstrates how to build type-safe, dynamic WHERE clauses without
 * concatenating raw SQL strings or writing massive nested IF statements.
 */
public class AuditLogSpecification {

    /**
     * Creates a {@link Specification} based on the provided search criteria.
     * All non-null fields in the request will be combined with AND.
     */
    public static Specification<AuditLogEntity> fromRequest(AuditSearchRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.transactionId() != null && !request.transactionId().isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("transactionId"), request.transactionId()));
            }

            if (request.accountId() != null && !request.accountId().isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("accountId"), request.accountId()));
            }

            if (request.verdict() != null) {
                predicates.add(criteriaBuilder.equal(root.get("verdict"), request.verdict()));
            }

            if (request.startDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("processedAt"), request.startDate()));
            }

            if (request.endDate() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("processedAt"), request.endDate()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
