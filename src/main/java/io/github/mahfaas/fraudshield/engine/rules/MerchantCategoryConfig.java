package io.github.mahfaas.fraudshield.engine.rules;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "merchant_category_config")
public class MerchantCategoryConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String category;

    @Column(nullable = false, length = 20)
    private String action; // DECLINE or REVIEW
}
