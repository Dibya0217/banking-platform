package com.banking.upi.service;

import com.banking.upi.dto.request.ChangePinRequest;
import com.banking.upi.dto.request.CreateVpaRequest;
import com.banking.upi.dto.request.UpiTransferRequest;
import com.banking.upi.dto.response.UpiIdResponse;
import com.banking.upi.dto.response.UpiTransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface UpiService {

    UpiIdResponse createVpa(CreateVpaRequest request, UUID customerId);

    void changePin(UUID upiIdId, ChangePinRequest request, UUID customerId);

    UpiTransactionResponse transfer(UpiTransferRequest request, UUID initiatedBy);

    List<UpiIdResponse> listVpas(UUID customerId);

    Page<UpiTransactionResponse> getTransactions(UUID upiIdId, UUID customerId, Pageable pageable);
}
