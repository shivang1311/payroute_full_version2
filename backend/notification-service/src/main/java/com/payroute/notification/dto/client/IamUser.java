package com.payroute.notification.dto.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Slim view of an iam-service UserResponse — only the fields broadcast fan-out needs.
 * Extra fields on the wire are ignored so iam-service can evolve independently.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamUser {
    private Long id;
    private String username;
    private String role;
    private boolean active;
}
