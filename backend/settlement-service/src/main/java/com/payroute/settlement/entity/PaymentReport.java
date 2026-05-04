package com.payroute.settlement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_name", nullable = false, length = 200)
    private String reportName;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private ReportScope scope;

    @Column(name = "parameters", columnDefinition = "JSON")
    private String parameters;

    @Column(name = "metrics", columnDefinition = "JSON")
    private String metrics;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;
}
