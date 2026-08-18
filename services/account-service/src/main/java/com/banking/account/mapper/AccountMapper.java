package com.banking.account.mapper;

import com.banking.account.dto.response.AccountResponse;
import com.banking.account.entity.Account;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    AccountResponse toResponse(Account account);
}
