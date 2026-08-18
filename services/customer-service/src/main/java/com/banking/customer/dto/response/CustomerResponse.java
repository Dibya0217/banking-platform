package com.banking.customer.dto.response;

import com.banking.customer.entity.CustomerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    private UUID id;
    private String fullName;
    private String email;
    private String mobile;
    private LocalDate dateOfBirth;
    private String gender;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String pincode;
    private CustomerStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
