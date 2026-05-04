package com.payroute.routing.service;

import com.payroute.routing.dto.request.RoutingRuleRequest;
import com.payroute.routing.dto.response.PagedResponse;
import com.payroute.routing.dto.response.RoutingRuleResponse;
import com.payroute.routing.entity.RoutingRule;
import com.payroute.routing.exception.ResourceNotFoundException;
import com.payroute.routing.mapper.RoutingRuleMapper;
import com.payroute.routing.repository.RoutingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingRuleService {

    private final RoutingRuleRepository routingRuleRepository;
    private final RoutingRuleMapper routingRuleMapper;

    @Transactional(readOnly = true)
    public PagedResponse<RoutingRuleResponse> getAllRules(Pageable pageable) {
        Page<RoutingRule> page = routingRuleRepository.findAllActive(pageable);
        return routingRuleMapper.toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public RoutingRuleResponse getRuleById(Long id) {
        RoutingRule rule = routingRuleRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", id));
        return routingRuleMapper.toResponse(rule);
    }

    @Transactional
    public RoutingRuleResponse createRule(RoutingRuleRequest request) {
        RoutingRule rule = routingRuleMapper.toEntity(request);
        rule.setActive(true);
        RoutingRule saved = routingRuleRepository.save(rule);
        log.info("Created routing rule: id={}, name={}", saved.getId(), saved.getName());
        return routingRuleMapper.toResponse(saved);
    }

    @Transactional
    public RoutingRuleResponse updateRule(Long id, RoutingRuleRequest request) {
        RoutingRule existing = routingRuleRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", id));
        routingRuleMapper.updateEntity(request, existing);
        RoutingRule saved = routingRuleRepository.save(existing);
        log.info("Updated routing rule: id={}, name={}", saved.getId(), saved.getName());
        return routingRuleMapper.toResponse(saved);
    }

    @Transactional
    public void deleteRule(Long id) {
        RoutingRule existing = routingRuleRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", id));
        existing.setDeletedAt(LocalDateTime.now());
        existing.setActive(false);
        routingRuleRepository.save(existing);
        log.info("Soft-deleted routing rule: id={}", id);
    }
}
