package com.payroute.exception.client;

import com.payroute.exception.config.FeignConfig;
import com.payroute.exception.dto.client.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "notification-service", configuration = FeignConfig.class)
public interface NotificationServiceClient {

    @PostMapping("/api/v1/notifications")
    ResponseEntity<Map<String, Object>> sendNotification(@RequestBody NotificationRequest request);
}
