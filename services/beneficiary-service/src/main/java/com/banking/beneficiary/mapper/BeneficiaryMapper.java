package com.banking.beneficiary.mapper;

import com.banking.beneficiary.dto.response.BeneficiaryResponse;
import com.banking.beneficiary.entity.Beneficiary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BeneficiaryMapper {

    @Mapping(target = "transferAllowed", expression = "java(beneficiary.isTransferAllowed())")
    BeneficiaryResponse toResponse(Beneficiary beneficiary);
}
