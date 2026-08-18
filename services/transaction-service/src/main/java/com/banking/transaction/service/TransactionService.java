package com.banking.transaction.service;

import com.banking.transaction.dto.request.DepositRequest;
import com.banking.transaction.dto.request.TransactionHistoryFilter;
import com.banking.transaction.dto.request.TransferRequest;
import com.banking.transaction.dto.request.WithdrawRequest;
import com.banking.transaction.dto.response.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TransactionService {

    TransactionResponse deposit(DepositRequest request, UUID initiatedBy);

    TransactionResponse withdraw(WithdrawRequest request, UUID initiatedBy);

    TransactionResponse transfer(TransferRequest request, UUID initiatedBy);

    TransactionResponse getById(UUID transactionId, UUID requesterId, boolean isAdmin);

    Page<TransactionResponse> getHistory(UUID accountId, TransactionHistoryFilter filter, Pageable pageable);

    TransactionResponse reverse(UUID transactionId, UUID requesterId);
}
