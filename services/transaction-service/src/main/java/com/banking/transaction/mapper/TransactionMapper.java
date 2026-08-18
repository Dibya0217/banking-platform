package com.banking.transaction.mapper;

import com.banking.transaction.dto.response.TransactionResponse;
import com.banking.transaction.entity.Transaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    TransactionResponse toResponse(Transaction transaction);
}
