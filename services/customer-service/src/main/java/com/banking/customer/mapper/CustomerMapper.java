package com.banking.customer.mapper;

import com.banking.customer.dto.response.CustomerResponse;
import com.banking.customer.entity.Customer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    CustomerResponse toResponse(Customer customer);
}
