package com.banking.statement.service;

import com.banking.statement.dto.response.StatementResponse;
import com.banking.statement.entity.Statement;

import java.util.UUID;

public interface StatementService {

    Statement generateStatement(UUID accountId, int month, int year);

    StatementResponse getStatement(UUID accountId, int month, int year);

    StatementResponse getOrGenerate(UUID accountId, int month, int year);

    byte[] downloadStatement(UUID accountId, int month, int year);
}
