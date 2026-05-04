package com.payroute.party.dto.client;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {

    private String userId;
    private String title;
    private String message;
    private String category;
    private String severity;
    private String referenceType;
    private Long referenceId;
}
