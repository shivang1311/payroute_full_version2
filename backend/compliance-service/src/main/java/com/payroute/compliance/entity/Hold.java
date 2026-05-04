package com.payroute.compliance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "hold")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "placed_by")
    private Long placedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private HoldStatus status;

    @Column(name = "release_notes")
    private String releaseNotes;

    @Column(name = "released_by")
    private Long releasedBy;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "placed_at")
    private LocalDateTime placedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
