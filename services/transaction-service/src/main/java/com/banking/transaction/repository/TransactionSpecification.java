package com.banking.transaction.repository;

import com.banking.transaction.dto.request.TransactionHistoryFilter;
import com.banking.transaction.entity.Transaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TransactionSpecification {

    private TransactionSpecification() {}

    public static Specification<Transaction> forAccount(UUID accountId, TransactionHistoryFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Account filter: from OR to
            predicates.add(cb.or(
                    cb.equal(root.get("fromAccountId"), accountId),
                    cb.equal(root.get("toAccountId"), accountId)
            ));

            if (filter.getType() != null) {
                predicates.add(cb.equal(root.get("transactionType"), filter.getType()));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            if (filter.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        filter.getDateFrom().atStartOfDay().toInstant(ZoneOffset.UTC)));
            }

            if (filter.getDateTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        filter.getDateTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
