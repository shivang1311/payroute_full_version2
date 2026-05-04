package com.payroute.routing.service;

import com.payroute.routing.dto.request.RouteRequest;
import com.payroute.routing.dto.response.RailInstructionResponse;
import com.payroute.routing.engine.RailSelector;
import com.payroute.routing.entity.RailInstruction;
import com.payroute.routing.entity.RailStatus;
import com.payroute.routing.entity.RailType;
import com.payroute.routing.entity.RoutingRule;
import com.payroute.routing.mapper.RailInstructionMapper;
import com.payroute.routing.repository.RailInstructionRepository;
import com.payroute.routing.repository.RoutingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingService {

    private final RoutingRuleRepository routingRuleRepository;
    private final RailInstructionRepository railInstructionRepository;
    private final RailSelector railSelector;
    private final RailInstructionMapper railInstructionMapper;
    private final RailSimulationService railSimulationService;

    @Value("${payroute.simulation.max-retries:3}")
    private int maxRetries;

    @Transactional
    public RailInstructionResponse routePayment(RouteRequest request) {
        log.info("Routing payment: paymentId={}, amount={}, currency={}",
                request.getPaymentId(), request.getAmount(), request.getCurrency());

        List<RoutingRule> activeRules = routingRuleRepository.findByActiveOrderByPriorityAsc();
        RailType selectedRail = railSelector.selectRail(activeRules, request);

        String correlationRef = generateCorrelationRef(request.getPaymentId());

        RailInstruction instruction = RailInstruction.builder()
                .paymentId(request.getPaymentId())
                .rail(selectedRail)
                .correlationRef(correlationRef)
                .railStatus(RailStatus.PENDING)
                .retryCount(0)
                .maxRetries(maxRetries)
                .build();

        RailInstruction saved = railInstructionRepository.save(instruction);
        log.info("Created rail instruction: id={}, rail={}, correlationRef={}",
                saved.getId(), saved.getRail(), saved.getCorrelationRef());

        railSimulationService.simulateRail(saved);

        return railInstructionMapper.toResponse(saved);
    }

    private String generateCorrelationRef(Long paymentId) {
        return "ROUTE-" + paymentId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
