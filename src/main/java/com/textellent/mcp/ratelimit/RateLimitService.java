package com.textellent.mcp.ratelimit;

import com.textellent.mcp.security.TenantContextHolder;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting service using Bucket4j.
 * Provides different rate limits for read vs write operations per tenant.
 */
@Service
public class RateLimitService {

    @Value("${ratelimit.read.capacity:100}")
    private long readCapacity;

    @Value("${ratelimit.read.refill-tokens:100}")
    private long readRefillTokens;

    @Value("${ratelimit.read.refill-duration-minutes:1}")
    private long readRefillDuration;

    @Value("${ratelimit.write.capacity:200}")
    private long writeCapacity;

    @Value("${ratelimit.write.refill-tokens:200}")
    private long writeRefillTokens;

    @Value("${ratelimit.write.refill-duration-minutes:1}")
    private long writeRefillDuration;

    private final Map<String, Bucket> readBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> writeBuckets = new ConcurrentHashMap<>();

    /**
     * Check if the current request is allowed under read rate limits.
     */
    public boolean allowRead() {
        String tenantId = getTenantKey();
        Bucket bucket = readBuckets.computeIfAbsent(tenantId, k -> createReadBucket());
        return bucket.tryConsume(1);
    }

    /**
     * Check if the current request is allowed under write rate limits.
     */
    public boolean allowWrite() {
        String tenantId = getTenantKey();
        Bucket bucket = writeBuckets.computeIfAbsent(tenantId, k -> createWriteBucket());
        return bucket.tryConsume(1);
    }

    /**
     * Get the number of available read tokens for current tenant.
     */
    public long getAvailableReadTokens() {
        String tenantId = getTenantKey();
        Bucket bucket = readBuckets.computeIfAbsent(tenantId, k -> createReadBucket());
        return bucket.getAvailableTokens();
    }

    /**
     * Get the number of available write tokens for current tenant.
     */
    public long getAvailableWriteTokens() {
        String tenantId = getTenantKey();
        Bucket bucket = writeBuckets.computeIfAbsent(tenantId, k -> createWriteBucket());
        return bucket.getAvailableTokens();
    }

    private Bucket createReadBucket() {
        Bandwidth limit = Bandwidth.classic(readCapacity,
            Refill.intervally(readRefillTokens, Duration.ofMinutes(readRefillDuration)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createWriteBucket() {
        Bandwidth limit = Bandwidth.classic(writeCapacity,
            Refill.intervally(writeRefillTokens, Duration.ofMinutes(writeRefillDuration)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String getTenantKey() {
        String tenantId = TenantContextHolder.getTenantId();
        return tenantId != null ? tenantId : "default";
    }
}
