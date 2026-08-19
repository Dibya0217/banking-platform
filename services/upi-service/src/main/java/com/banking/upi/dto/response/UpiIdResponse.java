package com.banking.upi.dto.response;

import com.banking.upi.entity.UpiStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class UpiIdResponse {
    private UUID id;
    private UUID customerId;
    private UUID accountId;
    private String vpa;
    private BigDecimal dailyLimit;
    private UpiStatus status;
    private Instant createdAt;
}
