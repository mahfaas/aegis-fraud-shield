package io.github.mahfaas.fraudshield.engine.rules;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantCategoryConfigRepository extends JpaRepository<MerchantCategoryConfig, Long> {
    Optional<MerchantCategoryConfig> findByCategory(String category);
    void deleteByCategory(String category);
}
