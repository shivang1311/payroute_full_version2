package com.payroute.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroute.notification.dto.request.WebhookEndpointRequest;
import com.payroute.notification.dto.request.WebhookEventRequest;
import com.payroute.notification.dto.response.WebhookDeliveryResponse;
import com.payroute.notification.dto.response.WebhookEndpointResponse;
import com.payroute.notification.entity.*;
import com.payroute.notification.exception.ResourceNotFoundException;
import com.payroute.notification.repository.WebhookDeliveryRepository;
import com.payroute.notification.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private static final String MASKED_SECRET = "••••••••";

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    // ---------- Endpoint CRUD ----------

    @Transactional
    public WebhookEndpointResponse create(Long userId, WebhookEndpointRequest req) {
        String secret = (req.getSecret() == null || req.getSecret().isBlank())
                ? generateSecret() : req.getSecret();

        WebhookEndpoint e = WebhookEndpoint.builder()
                .userId(userId)
                .name(req.getName())
                .url(req.getUrl())
                .secret(secret)
                .events(normalizeEvents(req.getEvents()))
                .active(req.getActive() == null ? Boolean.TRUE : req.getActive())
                .build();
        e = endpointRepository.save(e);
        log.info("Created webhook endpoint id={} userId={} url={}", e.getId(), userId, e.getUrl());
        // Return the actual secret once (so the user can copy it)
        return toResponseWithSecret(e);
    }

    @Transactional
    public WebhookEndpointResponse update(Long id, WebhookEndpointRequest req) {
        WebhookEndpoint e = endpointRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEndpoint", id));
        if (req.getName() != null) e.setName(req.getName());
        if (req.getUrl() != null) e.setUrl(req.getUrl());
        if (req.getEvents() != null) e.setEvents(normalizeEvents(req.getEvents()));
        if (req.getActive() != null) e.setActive(req.getActive());
        if (req.getSecret() != null && !req.getSecret().isBlank()) e.setSecret(req.getSecret());
        e = endpointRepository.save(e);
        return toResponseMasked(e);
    }

    @Transactional
    public void delete(Long id) {
        if (!endpointRepository.existsById(id)) {
            throw new ResourceNotFoundException("WebhookEndpoint", id);
        }
        endpointRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> listForUser(Long userId) {
        return endpointRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponseMasked).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WebhookEndpointResponse getOne(Long id) {
        return endpointRepository.findById(id).map(this::toResponseMasked)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEndpoint", id));
    }

    @Transactional(readOnly = true)
    public WebhookEndpointResponse rotateSecret(Long id) {
        WebhookEndpoint e = endpointRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEndpoint", id));
        e.setSecret(generateSecret());
        endpointRepository.save(e);
        return toResponseWithSecret(e);
    }

    // ---------- Event publishing (fan-out) ----------

    /**
     * Called by other services (payment-service) to enqueue deliveries
     * for every active endpoint that subscribes to this event.
     */
    @Transactional
    public int publish(WebhookEventRequest req) {
        List<WebhookEndpoint> all = endpointRepository.findByActiveTrue();
        List<WebhookEndpoint> matches = all.stream()
                // userId == 0 => global fan-out endpoint: receives every event regardless of event.userId
                .filter(e -> Long.valueOf(0L).equals(e.getUserId())
                        || req.getUserId() == null
                        || req.getUserId().equals(e.getUserId()))
                .filter(e -> subscribesTo(e, req.getEventType()))
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            log.debug("No subscribers for event {}", req.getEventType());
            return 0;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventType", req.getEventType().name());
        body.put("referenceId", req.getReferenceId());
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("data", req.getData() == null ? Collections.emptyMap() : req.getData());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }

        for (WebhookEndpoint ep : matches) {
            WebhookDelivery d = WebhookDelivery.builder()
                    .endpointId(ep.getId())
                    .eventType(req.getEventType())
                    .referenceId(req.getReferenceId())
                    .payload(payload)
                    .status(DeliveryStatus.PENDING)
                    .attempts(0)
                    .nextAttemptAt(LocalDateTime.now())
                    .build();
            deliveryRepository.save(d);
        }
        log.info("Enqueued {} webhook deliveries for event {}", matches.size(), req.getEventType());
        return matches.size();
    }

    // ---------- Deliveries (UI) ----------

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryResponse> listDeliveries(Long endpointId, Pageable pageable) {
        Page<WebhookDelivery> page = (endpointId != null)
                ? deliveryRepository.findByEndpointIdOrderByCreatedAtDesc(endpointId, pageable)
                : deliveryRepository.findAllByOrderByCreatedAtDesc(pageable);
        Map<Long, String> urlCache = new HashMap<>();
        return page.map(d -> {
            String url = urlCache.computeIfAbsent(d.getEndpointId(), id ->
                    endpointRepository.findById(id).map(WebhookEndpoint::getUrl).orElse("(deleted)"));
            return toDeliveryResponse(d, url);
        });
    }

    @Transactional
    public WebhookDeliveryResponse retry(Long id) {
        WebhookDelivery d = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookDelivery", id));
        d.setStatus(DeliveryStatus.RETRYING);
        d.setNextAttemptAt(LocalDateTime.now());
        d = deliveryRepository.save(d);
        return toDeliveryResponse(d, endpointRepository.findById(d.getEndpointId())
                .map(WebhookEndpoint::getUrl).orElse(""));
    }

    @Transactional
    public int enqueueTest(Long endpointId) {
        WebhookEndpoint e = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEndpoint", endpointId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "This is a test webhook from PayRoute Hub");
        data.put("endpointId", e.getId());

        return publish(WebhookEventRequest.builder()
                .eventType(WebhookEventType.PAYMENT_INITIATED)
                .referenceId(0L)
                .userId(e.getUserId())
                .data(data)
                .build());
    }

    // ---------- helpers ----------

    private boolean subscribesTo(WebhookEndpoint e, WebhookEventType type) {
        String ev = e.getEvents();
        if (ev == null || ev.isBlank() || "*".equals(ev.trim())) return true;
        for (String s : ev.split(",")) {
            if (s.trim().equalsIgnoreCase(type.name())) return true;
        }
        return false;
    }

    private String normalizeEvents(String events) {
        if (events == null) return null;
        String trimmed = events.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed)) return "*";
        return Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String generateSecret() {
        byte[] buf = new byte[32];
        new SecureRandom().nextBytes(buf);
        StringBuilder sb = new StringBuilder("whsec_");
        for (byte b : buf) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private WebhookEndpointResponse toResponseWithSecret(WebhookEndpoint e) {
        return WebhookEndpointResponse.builder()
                .id(e.getId()).userId(e.getUserId()).name(e.getName()).url(e.getUrl())
                .secret(e.getSecret()).events(e.getEvents()).active(e.getActive())
                .lastSuccessAt(e.getLastSuccessAt()).lastFailureAt(e.getLastFailureAt())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build();
    }

    private WebhookEndpointResponse toResponseMasked(WebhookEndpoint e) {
        WebhookEndpointResponse r = toResponseWithSecret(e);
        r.setSecret(MASKED_SECRET);
        return r;
    }

    private WebhookDeliveryResponse toDeliveryResponse(WebhookDelivery d, String url) {
        return WebhookDeliveryResponse.builder()
                .id(d.getId()).endpointId(d.getEndpointId()).endpointUrl(url)
                .eventType(d.getEventType()).referenceId(d.getReferenceId())
                .payload(d.getPayload()).status(d.getStatus()).attempts(d.getAttempts())
                .nextAttemptAt(d.getNextAttemptAt()).lastAttemptAt(d.getLastAttemptAt())
                .responseStatus(d.getResponseStatus()).responseBody(d.getResponseBody())
                .createdAt(d.getCreatedAt()).build();
    }
}
