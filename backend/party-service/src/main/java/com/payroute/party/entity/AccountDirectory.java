package com.payroute.party.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * A bank account linked to a {@link Party}. The same row shape stores both the
 * customer's own accounts and saved beneficiaries (no OWN/BENEFICIARY flag —
 * a beneficiary is just another row whose {@code partyId} equals the
 * customer's). Unique on (accountNumber, ifsc) to prevent duplicates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account_directory")
@EntityListeners(AuditingEntityListener.class)
public class AccountDirectory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "ifsc_iban", nullable = false, length = 50)
    private String ifscIban;

    @Column(length = 100)
    private String alias;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "account_type", nullable = false, length = 30)
    private String accountType;

    @Column(name = "vpa_upi_id", length = 100)
    private String vpaUpiId;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 120)
    private String email;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
