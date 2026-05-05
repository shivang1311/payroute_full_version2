package com.payroute.notification.client;

import com.payroute.notification.config.FeignConfig;
import com.payroute.notification.dto.client.IamUser;
import com.payroute.notification.dto.client.IamUserListResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Service-to-service client used to fan out broadcast notifications:
 * we ask iam-service for every active user with a given role, then create
 * one notification row per user.
 */
@FeignClient(name = "iam-service", configuration = FeignConfig.class)
public interface IamServiceClient {

    @GetMapping("/api/v1/users/by-role")
    IamUserListResponse getUsersByRole(@RequestParam("role") String role);

    /** Convenience for callers that only need the username list. */
    default List<IamUser> usersByRole(String role) {
        IamUserListResponse resp = getUsersByRole(role);
        return resp != null && resp.getData() != null ? resp.getData() : List.of();
    }
}
