package com.payroute.settlement.client;

import com.payroute.settlement.config.FeignConfig;
import com.payroute.settlement.dto.client.BroadcastNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "notification-service", configuration = FeignConfig.class)
public interface NotificationServiceClient {

    /** Fan-out to every active user with the given role (e.g. RECONCILIATION). */
    @PostMapping("/api/v1/notifications/broadcast")
    ResponseEntity<Map<String, Object>> broadcast(@RequestBody BroadcastNotificationRequest request);
}
