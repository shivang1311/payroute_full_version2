package com.payroute.ledger.service;

import com.payroute.ledger.dto.request.FeeScheduleRequest;
import com.payroute.ledger.dto.response.FeeScheduleResponse;
import com.payroute.ledger.entity.FeeSchedule;
import com.payroute.ledger.entity.FeeType;
import com.payroute.ledger.entity.RailType;
import com.payroute.ledger.exception.ResourceNotFoundException;
import com.payroute.ledger.mapper.FeeScheduleMapper;
import com.payroute.ledger.repository.FeeScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeeService {

    private final FeeScheduleRepository feeScheduleRepository;
    private final FeeScheduleMapper feeScheduleMapper;

    /**
     * Compute the fee for a given product, rail, amount, and date.
     * Looks up the active FeeSchedule, applies FLAT or PERCENTAGE logic,
     * and respects min/max bounds.
     */
    public BigDecimal computeFee(String product, RailType rail, BigDecimal amount, LocalDate date) {
        FeeSchedule schedule = feeScheduleRepository.findActiveSchedule(product, rail, date)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("No active fee schedule found for product=%s, rail=%s, date=%s",
                                product, rail, date)));

        BigDecimal fee;
        if (schedule.getFeeType() == FeeType.FLAT) {
            fee = schedule.getValue();
        } else {
            // PERCENTAGE: value is stored as percentage (e.g., 1.5 means 1.5%)
            fee = amount.multiply(schedule.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        // Apply min bound
        if (schedule.getMinFee() != null && fee.compareTo(schedule.getMinFee()) < 0) {
            fee = schedule.getMinFee();
        }

        // Apply max bound
        if (schedule.getMaxFee() != null && fee.compareTo(schedule.getMaxFee()) > 0) {
            fee = schedule.getMaxFee();
        }

        log.info("Computed fee for product={}, rail={}, amount={}: {}", product, rail, amount, fee);
        return fee.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public FeeScheduleResponse createFeeSchedule(FeeScheduleRequest request) {
        FeeSchedule entity = feeScheduleMapper.toEntity(request);
        entity.setActive(true);
        FeeSchedule saved = feeScheduleRepository.save(entity);
        log.info("Created fee schedule id={} for product={}, rail={}", saved.getId(), saved.getProduct(), saved.getRail());
        return feeScheduleMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public FeeScheduleResponse getFeeScheduleById(Long id) {
        FeeSchedule entity = feeScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FeeSchedule", "id", id));
        return feeScheduleMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<FeeScheduleResponse> getAllFeeSchedules() {
        return feeScheduleMapper.toResponseList(feeScheduleRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<FeeScheduleResponse> getActiveFeeSchedules() {
        return feeScheduleMapper.toResponseList(feeScheduleRepository.findByActiveTrue());
    }

    @Transactional
    public FeeScheduleResponse updateFeeSchedule(Long id, FeeScheduleRequest request) {
        FeeSchedule entity = feeScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FeeSchedule", "id", id));
        feeScheduleMapper.updateEntity(request, entity);
        FeeSchedule saved = feeScheduleRepository.save(entity);
        log.info("Updated fee schedule id={}", saved.getId());
        return feeScheduleMapper.toResponse(saved);
    }

    @Transactional
    public void deactivateFeeSchedule(Long id) {
        FeeSchedule entity = feeScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FeeSchedule", "id", id));
        entity.setActive(false);
        feeScheduleRepository.save(entity);
        log.info("Deactivated fee schedule id={}", id);
    }
}
