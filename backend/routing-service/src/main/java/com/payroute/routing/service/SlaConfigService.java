package com.payroute.routing.service;

import com.payroute.routing.dto.request.SlaConfigRequest;
import com.payroute.routing.dto.response.SlaConfigResponse;
import com.payroute.routing.entity.SlaConfig;
import com.payroute.routing.exception.ResourceNotFoundException;
import com.payroute.routing.repository.SlaConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SlaConfigService {

    private final SlaConfigRepository slaConfigRepository;

    public List<SlaConfigResponse> list() {
        return slaConfigRepository.findAll().stream().map(this::toResponse).toList();
    }

    public SlaConfigResponse getById(Long id) {
        SlaConfig s = slaConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SlaConfig", id));
        return toResponse(s);
    }

    @Transactional
    public SlaConfigResponse create(SlaConfigRequest req) {
        slaConfigRepository.findByRail(req.getRail()).ifPresent(existing -> {
            throw new IllegalArgumentException("SLA config already exists for rail " + req.getRail());
        });
        SlaConfig s = SlaConfig.builder()
                .rail(req.getRail())
                .targetTatSeconds(req.getTargetTatSeconds())
                .warningThresholdSeconds(req.getWarningThresholdSeconds())
                .active(req.getActive() == null ? Boolean.TRUE : req.getActive())
                .build();
        s = slaConfigRepository.save(s);
        log.info("Created SLA config for rail={} target={}s", s.getRail(), s.getTargetTatSeconds());
        return toResponse(s);
    }

    @Transactional
    public SlaConfigResponse update(Long id, SlaConfigRequest req) {
        SlaConfig s = slaConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SlaConfig", id));
        s.setRail(req.getRail());
        s.setTargetTatSeconds(req.getTargetTatSeconds());
        s.setWarningThresholdSeconds(req.getWarningThresholdSeconds());
        if (req.getActive() != null) s.setActive(req.getActive());
        s = slaConfigRepository.save(s);
        log.info("Updated SLA config id={} rail={} target={}s", s.getId(), s.getRail(), s.getTargetTatSeconds());
        return toResponse(s);
    }

    @Transactional
    public void delete(Long id) {
        if (!slaConfigRepository.existsById(id)) {
            throw new ResourceNotFoundException("SlaConfig", id);
        }
        slaConfigRepository.deleteById(id);
        log.info("Deleted SLA config id={}", id);
    }

    private SlaConfigResponse toResponse(SlaConfig s) {
        return SlaConfigResponse.builder()
                .id(s.getId())
                .rail(s.getRail())
                .targetTatSeconds(s.getTargetTatSeconds())
                .warningThresholdSeconds(s.getWarningThresholdSeconds())
                .active(s.getActive())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
