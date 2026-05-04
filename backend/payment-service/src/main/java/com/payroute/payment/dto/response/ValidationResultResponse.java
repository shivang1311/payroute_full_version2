package com.payroute.payment.dto.response;

import com.payroute.payment.entity.ValidationResultType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationResultResponse {

    private Long id;
    private String ruleName;
    private ValidationResultType result;
    private String message;
    private LocalDateTime checkedAt;
}
