package com.banking.customer.dto.response;

import com.banking.customer.entity.CustomerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRegistrationResponse {

    private UUID customerId;
    private CustomerStatus status;
    private String message;
}
