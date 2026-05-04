package com.payroute.notification.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Default "sink" endpoint used by the auto-seeded webhook so deliveries flow
 * to a 2xx receiver without any external network / extra process. Logs the
 * event type and delivery id and returns {@code 200 OK}.
 */
@RestController
@RequestMapping("/internal/webhook-sink")
@Slf4j
public class WebhookSinkController {

    @PostMapping
    public ResponseEntity<Map<String, Object>> sink(
            @RequestHeader(value = "X-PayRoute-Event", required = false) String event,
            @RequestHeader(value = "X-PayRoute-Delivery-Id", required = false) String deliveryId,
            @RequestBody(required = false) Map<String, Object> body) {
        log.info("Webhook sink received event={} deliveryId={} refId={}",
                event, deliveryId,
                body != null ? body.get("referenceId") : null);
        return ResponseEntity.ok(Map.of("received", true));
    }
}
