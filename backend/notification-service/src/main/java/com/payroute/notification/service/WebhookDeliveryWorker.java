package com.payroute.notification.service;

import com.payroute.notification.entity.DeliveryStatus;
import com.payroute.notification.entity.WebhookDelivery;
import com.payroute.notification.entity.WebhookEndpoint;
import com.payroute.notification.repository.WebhookDeliveryRepository;
import com.payroute.notification.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Polls webhook_delivery for due PENDING/RETRYING rows and attempts HTTP POST
 * with HMAC-SHA256 signature. Exponential backoff (1m, 5m, 30m, 2h, 12h).
 * After 5 attempts the delivery is marked FAILED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDeliveryWorker {

    private static final int MAX_ATTEMPTS = 5;
    // Backoff minutes for attempt N (index = attempts-already-made after success-of-current fails).
    // After attempt 1 fails -> wait 1m; after 2 fails -> 5m; 3 -> 30m; 4 -> 2h; 5 -> FAILED.
    private static final long[] BACKOFF_MINUTES = { 1, 5, 30, 120, 720 };

    // Per-tick batch size
    private static final int BATCH = 20;

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookEndpointRepository endpointRepository;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Scheduled(fixedDelayString = "${payroute.webhook.poll-ms:10000}")
    public void drain() {
        List<WebhookDelivery> due = deliveryRepository.findDue(
                Arrays.asList(DeliveryStatus.PENDING, DeliveryStatus.RETRYING),
                LocalDateTime.now(),
                PageRequest.of(0, BATCH));
        if (due.isEmpty()) return;
        log.debug("Webhook worker picked {} due delivery rows", due.size());
        for (WebhookDelivery d : due) {
            try {
                processOne(d.getId());
            } catch (Exception ex) {
                log.error("Worker error on delivery id={}", d.getId(), ex);
            }
        }
    }

    @Transactional
    public void processOne(Long deliveryId) {
        WebhookDelivery d = deliveryRepository.findById(deliveryId).orElse(null);
        if (d == null) return;
        if (d.getStatus() == DeliveryStatus.SUCCESS || d.getStatus() == DeliveryStatus.FAILED) return;

        WebhookEndpoint ep = endpointRepository.findById(d.getEndpointId()).orElse(null);
        if (ep == null || !Boolean.TRUE.equals(ep.getActive())) {
            d.setStatus(DeliveryStatus.FAILED);
            d.setResponseBody("Endpoint missing or inactive");
            d.setLastAttemptAt(LocalDateTime.now());
            deliveryRepository.save(d);
            return;
        }

        int nextAttempt = d.getAttempts() + 1;
        String sig = sign(d.getPayload(), ep.getSecret());
        LocalDateTime now = LocalDateTime.now();

        int respStatus = -1;
        String respBody = null;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ep.getUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-PayRoute-Event", d.getEventType().name())
                    .header("X-PayRoute-Delivery-Id", String.valueOf(d.getId()))
                    .header("X-PayRoute-Signature", "sha256=" + sig)
                    .POST(HttpRequest.BodyPublishers.ofString(d.getPayload(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            respStatus = resp.statusCode();
            respBody = truncate(resp.body(), 2000);
        } catch (Exception ex) {
            respBody = truncate("transport error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage(), 2000);
        }

        d.setAttempts(nextAttempt);
        d.setLastAttemptAt(now);
        d.setResponseStatus(respStatus < 0 ? null : respStatus);
        d.setResponseBody(respBody);

        boolean ok = respStatus >= 200 && respStatus < 300;
        if (ok) {
            d.setStatus(DeliveryStatus.SUCCESS);
            d.setNextAttemptAt(null);
            ep.setLastSuccessAt(now);
            endpointRepository.save(ep);
            log.info("Webhook delivery id={} succeeded (endpoint={}, status={})",
                    d.getId(), ep.getUrl(), respStatus);
        } else if (nextAttempt >= MAX_ATTEMPTS) {
            d.setStatus(DeliveryStatus.FAILED);
            d.setNextAttemptAt(null);
            ep.setLastFailureAt(now);
            endpointRepository.save(ep);
            log.warn("Webhook delivery id={} gave up after {} attempts", d.getId(), nextAttempt);
        } else {
            long waitMin = BACKOFF_MINUTES[Math.min(nextAttempt - 1, BACKOFF_MINUTES.length - 1)];
            d.setStatus(DeliveryStatus.RETRYING);
            d.setNextAttemptAt(now.plusMinutes(waitMin));
            ep.setLastFailureAt(now);
            endpointRepository.save(ep);
            log.info("Webhook delivery id={} will retry in {}m (attempts={}, status={})",
                    d.getId(), waitMin, nextAttempt, respStatus);
        }

        deliveryRepository.save(d);
    }

    private String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
