package io.github.mahfaas.fraudshield.cases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link FraudCaseNote}.
 *
 * <p>Inherits standard CRUD from {@link JpaRepository}.
 */
public interface FraudCaseNoteRepository extends JpaRepository<FraudCaseNote, Long> {

    /** Returns all notes for a case, newest first. */
    List<FraudCaseNote> findByFraudCaseIdOrderByCreatedAtDesc(Long fraudCaseId);
}
