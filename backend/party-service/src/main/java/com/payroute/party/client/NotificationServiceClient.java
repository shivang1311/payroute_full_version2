package com.payroute.party.client;

import com.payroute.party.config.FeignConfig;
import com.payroute.party.dto.client.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", configuration = FeignConfig.class)
public interface NotificationServiceClient {

    @PostMapping("/api/v1/notifications")
    void sendNotification(@RequestBody NotificationRequest request);
}
