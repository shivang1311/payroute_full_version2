package com.payroute.payment.client;

import com.payroute.payment.config.FeignConfig;
import com.payroute.payment.dto.client.NotificationRequest;
import com.payroute.payment.dto.client.WebhookEventRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", configuration = FeignConfig.class)
public interface NotificationServiceClient {

    @PostMapping("/api/v1/notifications")
    void sendNotification(@RequestBody NotificationRequest request);

    @PostMapping("/api/v1/webhooks/events")
    void publishWebhookEvent(@RequestBody WebhookEventRequest request);
}
