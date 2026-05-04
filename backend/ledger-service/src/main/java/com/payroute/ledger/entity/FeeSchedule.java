package com.payroute.ledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_schedule", indexes = {
        @Index(name = "idx_fee_product_rail", columnList = "product, rail")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class FeeSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product", nullable = false, length = 50)
    private String product;

    @Enumerated(EnumType.STRING)
    @Column(name = "rail", nullable = false, length = 10)
    private RailType rail;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, length = 15)
    private FeeType feeType;

    @Column(name = "value", nullable = false, precision = 12, scale = 4)
    private BigDecimal value;

    @Column(name = "min_fee", precision = 12, scale = 4)
    private BigDecimal minFee;

    @Column(name = "max_fee", precision = 12, scale = 4)
    private BigDecimal maxFee;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Version
    @Column(name = "version")
    private Integer version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
