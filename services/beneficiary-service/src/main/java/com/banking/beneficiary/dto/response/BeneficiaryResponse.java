package com.banking.beneficiary.dto.response;

import com.banking.beneficiary.entity.BeneficiaryStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class BeneficiaryResponse {

    private UUID id;
    private UUID customerId;
    private String accountNumber;
    private String ifscCode;
    private String beneficiaryName;
    private String bankName;
    private String nickName;
    private BeneficiaryStatus status;
    private boolean transferAllowed;
    private Instant transferEnabledAt;
    private Instant createdAt;
}
