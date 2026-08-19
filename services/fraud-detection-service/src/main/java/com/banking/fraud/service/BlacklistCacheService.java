package com.banking.fraud.service;

import com.banking.fraud.entity.BlacklistedAccount;
import com.banking.fraud.repository.BlacklistedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistCacheService {

    private static final String BLACKLIST_KEY = "fraud:blacklist:accounts";

    private final StringRedisTemplate redisTemplate;
    private final BlacklistedAccountRepository blacklistedAccountRepository;

    public boolean isBlacklisted(UUID accountId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(BLACKLIST_KEY, accountId.toString()));
    }

    public void addToBlacklist(UUID accountId) {
        redisTemplate.opsForSet().add(BLACKLIST_KEY, accountId.toString());
    }

    public void removeFromBlacklist(UUID accountId) {
        redisTemplate.opsForSet().remove(BLACKLIST_KEY, accountId.toString());
    }

    @Scheduled(fixedDelayString = "${fraud.blacklist.refresh-interval-ms:300000}")
    public void refreshBlacklistCache() {
        try {
            List<BlacklistedAccount> blacklisted = blacklistedAccountRepository.findAll();
            redisTemplate.delete(BLACKLIST_KEY);
            if (!blacklisted.isEmpty()) {
                String[] accountIds = blacklisted.stream()
                        .map(b -> b.getAccountId().toString())
                        .toArray(String[]::new);
                redisTemplate.opsForSet().add(BLACKLIST_KEY, accountIds);
            }
            log.debug("Refreshed blacklist cache: {} accounts", blacklisted.size());
        } catch (Exception e) {
            log.error("Failed to refresh blacklist cache: {}", e.getMessage(), e);
        }
    }
}
