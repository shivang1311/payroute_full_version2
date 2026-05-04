package com.payroute.compliance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "compliance_check")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplianceCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false)
    private CheckType checkType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private CheckSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private CheckResult result;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "checked_by")
    private String checkedBy;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
