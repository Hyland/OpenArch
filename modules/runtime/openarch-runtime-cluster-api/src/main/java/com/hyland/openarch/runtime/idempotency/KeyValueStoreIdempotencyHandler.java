/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hyland.openarch.runtime.idempotency;

import static com.hyland.openarch.runtime.idempotency.FailedOutcome.MESSAGE_FIELD;
import static com.hyland.openarch.runtime.idempotency.FailedOutcome.STATUS_CODE_FIELD;
import static com.hyland.openarch.runtime.idempotency.IdempotencyEntry.CLAIM_ID_FIELD;
import static com.hyland.openarch.runtime.idempotency.IdempotencyEntry.FAILURE_FIELD;
import static com.hyland.openarch.runtime.idempotency.IdempotencyEntry.PENDING_FIELD;
import static com.hyland.openarch.runtime.idempotency.IdempotencyEntry.TYPENAME_FIELD;
import static com.hyland.openarch.runtime.idempotency.IdempotencyEntry.VALUE_FIELD;

import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.runtime.kv.KeyValueStore;
import com.hyland.openarch.runtime.kv.KeyValueService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Implementation of {@link IdempotencyHandler} that stores results in a {@link KeyValueService}
 * for persistent idempotency across requests.
 * <p>
 * Results are serialized to bytes and stored with an expiration TTL to prevent unbounded cache
 * growth. The cache key is derived from the idempotency key and a namespace prefix. Result classes
 * must be registered in {@link IdempotencyResultTypeRegistry}; their stable registry keys are
 * persisted in cache entries instead of Java class names.
 * <p>
 * At-most-once execution is enforced by atomically claiming the cache key with
 * {@link KeyValueStore#compareAndSet(String, String, String, long)} before the operation runs: a
 * leased, per-claim owner marker is CAS-written in place of a plain read-then-write, so concurrent
 * callers racing on the same key cannot both observe a miss and both execute the operation. That
 * marker is itself an {@link IdempotencyEntry} with {@code pending = true} carrying a unique
 * {@code claimId}, so a cache entry always holds the very same JSON document shape whether it is
 * claimed, resolved with a value, or resolved with a failure. Callers that lose the race wait,
 * polling for either the winner's published result or the lease to expire, and throw
 * {@link IdempotencyClaimTimeoutException} if neither happens in time. Because each claim owns a
 * unique {@code claimId}, a caller whose lease has already expired and been reclaimed by someone
 * else can never mistake the new claimant's marker for its own, so it can safely detect and discard
 * a stale outcome instead of overwriting the current claimant's state. The lease should be sized to
 * comfortably exceed the expected duration of the guarded operation.
 */
public final class KeyValueStoreIdempotencyHandler implements IdempotencyHandler {

    private static final Logger log = LogManager.getLogger(KeyValueStoreIdempotencyHandler.class);

    /** Default lease duration for a claim, in seconds, capped by the configured result TTL. */
    private static final long DEFAULT_LEASE_SECONDS = 60L;

    /** Interval between polls while waiting for a concurrent claim to resolve, in milliseconds. */
    private static final long CLAIM_POLL_INTERVAL_MILLIS = 100L;

    /** Extra grace period added to the lease when computing the wait deadline, in milliseconds. */
    private static final long CLAIM_WAIT_BUFFER_MILLIS = 2_000L;

    private final KeyValueStore keyValueStore;

    private final String namespace;

    private final long ttlSeconds;

    private final long leaseSeconds;

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    /**
     * Creates a new idempotency handler using the given key-value service.
     *
     * @param keyValueService the key-value service for obtaining stores
     * @param storeName       the name of the key-value store to use
     * @param namespace       prefix for cache keys (e.g., "idempotency:commands:")
     * @param ttlSeconds      time-to-live for cached results in seconds
     * @throws NullPointerException     if the key-value service, store name or namespace is null
     * @throws IllegalArgumentException if the store name or namespace is blank, or the TTL is not positive
     */
    public KeyValueStoreIdempotencyHandler(KeyValueService keyValueService, String storeName, String namespace,
            long ttlSeconds) {
        Objects.requireNonNull(keyValueService, "KeyValueService cannot be null");
        Objects.requireNonNull(storeName, "Store name cannot be null");
        Objects.requireNonNull(namespace, "Namespace cannot be null");
        if (storeName.isBlank()) {
            throw new IllegalArgumentException("Store name cannot be empty");
        }
        if (namespace.isBlank()) {
            throw new IllegalArgumentException("Namespace cannot be empty");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        this.keyValueStore = keyValueService.getKeyValueStore(storeName);
        this.namespace = namespace;
        this.ttlSeconds = ttlSeconds;
        this.leaseSeconds = Math.max(1L, Math.min(ttlSeconds, DEFAULT_LEASE_SECONDS));
    }

    @Override
    public <T> Outcome<T> execute(String key, Supplier<T> operation) {
        return executeWithOutcome(key, null, operation);
    }

    @Override
    public <T> Outcome<T> execute(String key, String typename, Supplier<T> operation) {
        return executeWithOutcome(key, typename, operation);
    }

    private <T> Outcome<T> executeWithOutcome(String key, String typename,
            Supplier<T> operation) {
        String entryKey = getCacheKey(key);

        ClaimOutcome claim = claimOrAwaitCached(entryKey, key);
        if (claim.cachedResult() != null) {
            log.debug("Idempotency cache hit for key={}", sanitizeForLog(entryKey));
            try {
                return (Outcome<T>) claim.cachedResult().unwrap();
            } catch (IllegalArgumentException e) {
                throw new IdempotencyPersistenceException(
                        "Failed to replay the cached idempotency result for key=" + entryKey, e);
            }
        }

        T result = runAndFinalize(entryKey, claim.ownerMarker(), operation, typename);
        return new Outcome<>(result, null, false);
    }

    /**
     * Runs {@code operation}, which was just granted the claim identified by {@code ownerMarker},
     * and finalizes the cache entry with its outcome (success or failure).
     */
    private <T> T runAndFinalize(String entryKey, String ownerMarker,
            Supplier<T> operation, String typename) {
        T result;
        try {
            result = operation.get();
        } catch (RuntimeException e) {
            persistQuietly(entryKey, ownerMarker, IdempotencyEntry.failure(e));
            log.debug("Idempotency cache stored failure for key={}", sanitizeForLog(entryKey));
            throw e;
        }
        publishSuccess(entryKey, ownerMarker, result, typename);
        return result;
    }

    /**
     * Records a successful outcome. If the result cannot be represented as JSON it is not cached —
     * the claim is released instead, so the failure to record never turns a success into a
     * replayed failure and never leaves waiters blocked until the lease expires.
     * <p>
     * When the caller did not supply a {@code typename} — which is mandatory for a generic result
     * type, whose element types erasure would otherwise lose — the key registered for the result's
     * runtime class is used instead.
     */
    private <T> void publishSuccess(String entryKey, String ownerMarker, T result, String typename) {
        IdempotencyEntry successResult;
        try {
            JsonNode jsonNode = OBJECT_MAPPER.valueToTree(result);
            String resultTypeKey = typename != null ? typename
                    : IdempotencyResultTypeRegistry.getKey(result.getClass());
            if (resultTypeKey == null) {
                log.error("Idempotency result for key={} has unregistered type {}; releasing the claim without "
                        + "caching", sanitizeForLog(entryKey), result.getClass().getName());
                releaseClaim(entryKey, ownerMarker);
                return;
            }
            successResult = IdempotencyEntry.success(jsonNode, resultTypeKey);
        } catch (JacksonException e) {
            log.error("Idempotency result for key={} could not be converted to JSON; releasing the claim without "
                    + "caching, at-most-once is not guaranteed for this call", sanitizeForLog(entryKey), e);
            releaseClaim(entryKey, ownerMarker);
            return;
        }
        persistQuietly(entryKey, ownerMarker, successResult);
        log.debug("Idempotency cache stored success for key={}", sanitizeForLog(entryKey));
    }

    /**
     * Finalizes the cache entry, downgrading a persistence failure to an error log: the operation
     * already ran, so its outcome must still be surfaced to the caller.
     */
    private void persistQuietly(String entryKey, String ownerMarker, IdempotencyEntry output) {
        try {
            finalizeResult(entryKey, ownerMarker, output);
        } catch (IdempotencyPersistenceException e) {
            log.error("Idempotency result for key={} could not be persisted; at-most-once is not "
                    + "guaranteed for this call", sanitizeForLog(entryKey), e);
        }
    }

    /**
     * Clears this claim's marker so a subsequent caller can claim the key immediately, unless the
     * claim was already reclaimed by someone else.
     */
    private void releaseClaim(String cacheKey, String ownerMarker) {
        try {
            keyValueStore.compareAndSet(cacheKey, ownerMarker, null);
        } catch (RuntimeException e) {
            log.warn("Failed to release idempotency claim for key={}: {}", sanitizeForLog(cacheKey), e.getMessage());
        }
    }

    boolean isCached(String key) {
        String cacheKey = getCacheKey(key);
        IdempotencyEntry current = readOutput(cacheKey);
        return current != null && !current.pending();
    }

    @Override
    public boolean evict(String key) {
        String cacheKey = getCacheKey(key);
        String current = keyValueStore.getString(cacheKey);
        IdempotencyEntry decoded = current == null ? null : decode(cacheKey, current);
        if (decoded != null && !decoded.pending()) {
            return keyValueStore.compareAndSet(cacheKey, current, null);
        }
        return false;
    }

    @Override
    public boolean isOperationInProgress(String key) {
        String cacheKey = getCacheKey(key);
        IdempotencyEntry current = readOutput(cacheKey);
        return current != null && current.pending();
    }

    private IdempotencyEntry readOutput(String cacheKey) {
        String current = keyValueStore.getString(cacheKey);
        return current == null ? null : decode(cacheKey, current);
    }

    private String getCacheKey(String key) {
        return namespace + key;
    }

    /**
     * Neutralizes characters that would let a client-supplied idempotency key forge or split log
     * entries (CR, LF, and tab) before the value is interpolated into a log message. The
     * idempotency key ultimately comes from an untrusted caller (e.g. the {@code Idempotency-Key}
     * request header), so it must never be logged verbatim.
     */
    static String sanitizeForLog(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n\\t]", "_");
    }

    /**
     * Atomically claims {@code cacheKey} for exclusive execution, or returns the already-published
     * result if one exists.
     * <p>
     * Returns a {@link ClaimOutcome} carrying either the already-published {@link IdempotencyEntry}
     * (cache hit), or a fresh, unique owner marker when this call won the claim — the caller must
     * then run the operation and pass that owner marker to {@link #finalizeResult}. If another
     * caller currently holds the claim, this method waits (bounded by the lease duration plus a
     * small buffer) for it to either publish a result or have its lease expire, throwing
     * {@link IdempotencyClaimTimeoutException} if neither happens in time.
     */
    private ClaimOutcome claimOrAwaitCached(String entryKey, String keyValue) {
        String ownerMarker = newClaimMarker();
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(leaseSeconds).toNanos()
                + Duration.ofMillis(CLAIM_WAIT_BUFFER_MILLIS).toNanos();
        String current = keyValueStore.getString(entryKey);
        while (true) {
            IdempotencyEntry cached = current == null ? null : decode(entryKey, current);
            if (cached == null) {
                // Empty slot, or neither a valid result nor a claim marker (e.g. corrupted data):
                // claim it atomically, expecting the exact value we just observed.
                if (keyValueStore.compareAndSet(entryKey, current, ownerMarker, leaseSeconds)) {
                    return ClaimOutcome.won(ownerMarker);
                }
                // Lost the race against another caller: re-read and re-evaluate.
                checkClaimDeadline(deadlineNanos, keyValue);
            } else if (cached.pending()) {
                checkClaimDeadline(deadlineNanos, keyValue);
                sleepQuietly(CLAIM_POLL_INTERVAL_MILLIS);
            } else {
                return ClaimOutcome.hit(cached);
            }
            current = keyValueStore.getString(entryKey);
        }
    }

    /**
     * Throws {@link IdempotencyClaimTimeoutException} once the claim wait deadline has passed, so
     * a claim that can never be acquired fails fast instead of spinning indefinitely.
     */
    private static void checkClaimDeadline(long deadlineNanos, String keyValue) {
        if (System.nanoTime() >= deadlineNanos) {
            throw new IdempotencyClaimTimeoutException(
                    "Timed out waiting for a concurrent idempotent operation to complete for key=" + keyValue);
        }
    }

    /**
     * Publishes the final result, atomically handing the key off from this claim's owner marker.
     * <p>
     * If the lease already expired and the current value no longer matches {@code ownerMarker}
     * (e.g. another caller reclaimed the slot after timing us out, or already published its own
     * result), this caller's outcome is stale: it is discarded (logged as a warning) rather than
     * force-written, since the current value belongs to whoever reclaimed the key. This indicates
     * the configured lease was too short for the operation's actual duration.
     */
    private void finalizeResult(String cacheKey, String ownerMarker,
            IdempotencyEntry result) {
        String serialized = serialize(result);
        boolean stored;
        try {
            stored = keyValueStore.compareAndSet(cacheKey, ownerMarker, serialized, ttlSeconds);
        } catch (RuntimeException e) {
            throw new IdempotencyPersistenceException("Failed to persist idempotency result for key=" + cacheKey, e);
        }
        if (!stored) {
            log.warn("Idempotency claim for key={} was reclaimed by another caller before this result could be "
                    + "finalized; discarding this outcome (the lease was too short for the operation's actual "
                    + "duration)", sanitizeForLog(cacheKey));
            // Do NOT put(): the current value belongs to whoever reclaimed the key.
        }
    }


    /**
     * Builds a fresh, unique in-progress marker: an {@link IdempotencyEntry} with
     * {@code pending = true} and a random {@code claimId}, serialized with the same mapper used
     * for results. Uniqueness is what allows {@link #finalizeResult} to verify it still owns the
     * claim before publishing.
     */
    private static String newClaimMarker() {
        return serialize(IdempotencyEntry.pending(UUID.randomUUID().toString()));
    }

    /**
     * Outcome of {@link #claimOrAwaitCached}: either an already-published result (cache hit), or a
     * fresh owner marker for a claim this call just won. Exactly one of the two fields is non-null.
     */
    record ClaimOutcome(IdempotencyEntry cachedResult, String ownerMarker) {

        static ClaimOutcome hit(IdempotencyEntry cachedResult) {
            return new ClaimOutcome(cachedResult, null);
        }

        static ClaimOutcome won(String ownerMarker) {
            return new ClaimOutcome(null, ownerMarker);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ClaimOutcome other)) {
                return false;
            }
            return Objects.equals(cachedResult, other.cachedResult) && Objects.equals(ownerMarker, other.ownerMarker);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cachedResult, ownerMarker);
        }

        @Override
        public String toString() {
            return "ClaimOutcome[cachedResult=" + cachedResult + ", ownerMarker=" + ownerMarker + "]";
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(getRetryInterval(millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdempotencyClaimTimeoutException("Interrupted while waiting for an idempotency claim", e);
        }
    }

    private static long getRetryInterval(long millis) {
        Random random = ThreadLocalRandom.current();
        var jitterFactor = 0.5; // 50% jitter
        double factor = 1.0 + (random.nextDouble() * 2 - 1) * jitterFactor;
        return (long) (millis * factor);
    }

    /**
     * Decodes a raw cache entry into an {@link IdempotencyEntry}.
     *
     * @return the decoded output — pending, successful or failed — or {@code null} if the entry is
     *         absent, empty or cannot be interpreted (e.g. corrupted or written by an older format)
     */
    private IdempotencyEntry decode(String cacheKey, String entryJson) {
        try {
            if (StringUtils.isEmpty(entryJson)) {
                return null;
            }
            JsonNode node = OBJECT_MAPPER.readTree(entryJson);
            if (node == null || node.isNull() || !node.isObject()) {
                return null;
            }
            JsonNode pendingNode = node.get(PENDING_FIELD);
            if (pendingNode != null && pendingNode.booleanValue()) {
                JsonNode claimIdNode = node.get(CLAIM_ID_FIELD);
                String claimId = claimIdNode == null || claimIdNode.isNull() ? null : claimIdNode.asString();
                return IdempotencyEntry.pending(claimId);
            }
            JsonNode failureNode = node.get(FAILURE_FIELD);
            if (failureNode != null && !failureNode.isNull()) {
                return IdempotencyEntry.failure(decodeException(failureNode));
            }
            JsonNode valueNode = node.get(VALUE_FIELD);
            JsonNode typenameNode = node.get(TYPENAME_FIELD);
            if (valueNode != null && typenameNode != null && !typenameNode.isNull()) {
                return IdempotencyEntry.success(valueNode, typenameNode.asString());
            }
            return null;
        } catch (RuntimeException e) {
            log.warn("Failed to decode idempotency result from cache for key={}: {}", sanitizeForLog(cacheKey),
                    e.getMessage());
            return null;
        }
    }

    private static FailedOutcome decodeException(JsonNode exceptionNode) {
        JsonNode messageNode = exceptionNode.get(MESSAGE_FIELD);
        JsonNode httpErrorCodeNode = exceptionNode.get(STATUS_CODE_FIELD);
        String message = messageNode == null || messageNode.isNull() ? null : messageNode.asString();
        int httpErrorCode = httpErrorCodeNode == null || httpErrorCodeNode.isNull() ? 0
                : httpErrorCodeNode.asInt();
        return new FailedOutcome(message, httpErrorCode);
    }

    /**
     * Serializes an {@link IdempotencyEntry} for storage in the key-value store.
     *
     * @throws IdempotencyPersistenceException if the output cannot be serialized; never returns
     *         {@code null}, which the store would interpret as a deletion
     */
    private static String serialize(IdempotencyEntry result) {
        try {
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (JacksonException e) {
            throw new IdempotencyPersistenceException("Failed to serialize idempotency result", e);
        }
    }

    static final class IdempotencyPersistenceException extends RuntimeException {
        IdempotencyPersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
