package com.payroute.compliance.client;

import com.payroute.compliance.config.FeignConfig;
import com.payroute.compliance.dto.client.BroadcastNotificationRequest;
import com.payroute.compliance.dto.client.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "notification-service", configuration = FeignConfig.class)
public interface NotificationServiceClient {

    @PostMapping("/api/v1/notifications")
    ResponseEntity<Map<String, Object>> sendNotification(@RequestBody NotificationRequest request);

    /** Fan-out to every active user with the given role (e.g. all COMPLIANCE analysts). */
    @PostMapping("/api/v1/notifications/broadcast")
    ResponseEntity<Map<String, Object>> broadcast(@RequestBody BroadcastNotificationRequest request);
}
