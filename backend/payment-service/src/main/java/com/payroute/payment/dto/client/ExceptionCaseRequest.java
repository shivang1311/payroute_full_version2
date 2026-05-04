package com.payroute.payment.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbound DTO sent from payment-service → exception-service when a payment
 * fails and an ExceptionCase needs to be auto-raised. Mirrors the shape of
 * exception-service's ExceptionCaseRequest. Uses plain String for category
 * and priority so the two services don't need a shared enum dependency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionCaseRequest {
    private Long paymentId;
    private String category;   // VALIDATION | RAIL | POSTING | COMPLIANCE | SYSTEM
    private String description;
    private Long ownerId;
    private String priority;   // LOW | MEDIUM | HIGH | CRITICAL
}
