package com.banking.upi.mapper;

import com.banking.upi.dto.response.UpiIdResponse;
import com.banking.upi.dto.response.UpiTransactionResponse;
import com.banking.upi.entity.UpiId;
import com.banking.upi.entity.UpiTransaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UpiMapper {

    UpiIdResponse toResponse(UpiId upiId);

    UpiTransactionResponse toResponse(UpiTransaction upiTransaction);
}
