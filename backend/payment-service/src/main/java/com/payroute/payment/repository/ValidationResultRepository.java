package com.payroute.payment.repository;

import com.payroute.payment.entity.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationResultRepository extends JpaRepository<ValidationResult, Long> {

    List<ValidationResult> findByPaymentId(Long paymentId);
}
