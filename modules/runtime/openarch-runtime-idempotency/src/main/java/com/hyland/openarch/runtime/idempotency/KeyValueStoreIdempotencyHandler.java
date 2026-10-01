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

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
 * marker is itself an {@link IdempotencyOutput} with {@code pending = true} carrying a unique
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
     */
    public KeyValueStoreIdempotencyHandler(KeyValueService keyValueService, String storeName, String namespace,
            long ttlSeconds) {
        this.keyValueStore = keyValueService.getKeyValueStore(storeName);
        this.namespace = namespace;
        this.ttlSeconds = ttlSeconds;
        this.leaseSeconds = Math.max(1L, Math.min(ttlSeconds, DEFAULT_LEASE_SECONDS));
    }

    @Override
    public <T> Outcome<T> execute(String key, IdempotentOperation<T> operation) {
        return executeWithOutcome(key, null, operation);
    }

    @Override
    public <T> Outcome<T> execute(String key, String typename,
            IdempotentOperation<T> operation) {
        return executeWithOutcome(key, typename, operation);
    }

    private <T> Outcome<T> executeWithOutcome(String key, String typename,
            IdempotentOperation<T> operation) {
        String cacheKey = getCacheKey(key);

        ClaimOutcome claim = claimOrAwaitCached(cacheKey, key);
        if (claim.cachedResult() != null) {
            log.debug("Idempotency cache hit for key={}", sanitizeForLog(key));
            Object unwrapped = unwrap(claim.cachedResult());
            if (unwrapped instanceof FailedOutCome failedOutCome) {
                return new Outcome<>(null, failedOutCome, true);
            }
            return new Outcome<>((T) unwrapped, null, true);
        }

        T result = runAndFinalize(cacheKey, key, claim.ownerMarker(), operation, typename);
        return new Outcome<>(result, null, false);
    }

    /**
     * Runs {@code operation}, which was just granted the claim identified by {@code ownerMarker},
     * and finalizes the cache entry with its outcome (success or failure).
     */
    private <T> T runAndFinalize(String cacheKey, String key, String ownerMarker,
            IdempotentOperation<T> operation, String typename) {
        T result;
        try {
            result = operation.execute();
        } catch (RuntimeException e) {
            persistQuietly(cacheKey, key, ownerMarker, IdempotencyOutput.failure(e));
            log.debug("Idempotency cache stored failure for key={}", sanitizeForLog(key));
            throw e;
        }
        publishSuccess(cacheKey, key, ownerMarker, result, typename);
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
    private <T> void publishSuccess(String cacheKey, String key, String ownerMarker, T result, String typename) {
        IdempotencyOutput successResult;
        try {
            JsonNode jsonNode = OBJECT_MAPPER.valueToTree(result);
            String resultTypeKey = typename != null ? typename
                    : IdempotencyResultTypeRegistry.getKey(result.getClass());
            if (resultTypeKey == null) {
                log.error("Idempotency result for key={} has unregistered type {}; releasing the claim without "
                        + "caching", sanitizeForLog(key), result.getClass().getName());
                releaseClaim(cacheKey, ownerMarker);
                return;
            }
            successResult = IdempotencyOutput.success(jsonNode, resultTypeKey);
        } catch (JacksonException e) {
            log.error("Idempotency result for key={} could not be converted to JSON; releasing the claim without "
                    + "caching, at-most-once is not guaranteed for this call", sanitizeForLog(key), e);
            releaseClaim(cacheKey, ownerMarker);
            return;
        }
        persistQuietly(cacheKey, key, ownerMarker, successResult);
        log.debug("Idempotency cache stored success for key={}", sanitizeForLog(key));
    }

    /**
     * Finalizes the cache entry, downgrading a persistence failure to an error log: the operation
     * already ran, so its outcome must still be surfaced to the caller.
     */
    private void persistQuietly(String cacheKey, String key, String ownerMarker, IdempotencyOutput output) {
        try {
            finalizeResult(cacheKey, ownerMarker, output);
        } catch (IdempotencyPersistenceException e) {
            log.error("Idempotency result for key={} could not be persisted; at-most-once is not "
                    + "guaranteed for this call", sanitizeForLog(key), e);
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
        IdempotencyOutput current = readOutput(cacheKey);
        return current != null && !current.pending();
    }

    @Override
    public boolean evict(String key) {
        String cacheKey = getCacheKey(key);
        String current = keyValueStore.getString(cacheKey);
        IdempotencyOutput decoded = current == null ? null : decode(current, cacheKey);
        if (decoded != null && !decoded.pending()) {
            return keyValueStore.compareAndSet(cacheKey, current, null);
        }
        return false;
    }

    @Override
    public boolean isOperationInProgress(String key) {
        String cacheKey = getCacheKey(key);
        IdempotencyOutput current = readOutput(cacheKey);
        return current != null && current.pending();
    }

    private IdempotencyOutput readOutput(String cacheKey) {
        String current = keyValueStore.getString(cacheKey);
        return current == null ? null : decode(current, cacheKey);
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
     * Returns a {@link ClaimOutcome} carrying either the already-published {@link IdempotencyOutput}
     * (cache hit), or a fresh, unique owner marker when this call won the claim — the caller must
     * then run the operation and pass that owner marker to {@link #finalizeResult}. If another
     * caller currently holds the claim, this method waits (bounded by the lease duration plus a
     * small buffer) for it to either publish a result or have its lease expire, throwing
     * {@link IdempotencyClaimTimeoutException} if neither happens in time.
     */
    private ClaimOutcome claimOrAwaitCached(String cacheKey, String keyValue) {
        String ownerMarker = newClaimMarker();
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(leaseSeconds).toNanos()
                + Duration.ofMillis(CLAIM_WAIT_BUFFER_MILLIS).toNanos();
        String current = keyValueStore.getString(cacheKey);
        while (true) {
            IdempotencyOutput cached = current == null ? null : decode(current, cacheKey);
            if (cached == null) {
                // Empty slot, or neither a valid result nor a claim marker (e.g. corrupted data):
                // claim it atomically, expecting the exact value we just observed.
                if (keyValueStore.compareAndSet(cacheKey, current, ownerMarker, leaseSeconds)) {
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
            current = keyValueStore.getString(cacheKey);
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
            IdempotencyOutput result) {
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
     * Builds a fresh, unique in-progress marker: an {@link IdempotencyOutput} with
     * {@code pending = true} and a random {@code claimId}, serialized with the same mapper used
     * for results. Uniqueness is what allows {@link #finalizeResult} to verify it still owns the
     * claim before publishing.
     */
    private static String newClaimMarker() {
        return serialize(IdempotencyOutput.pending(UUID.randomUUID().toString()));
    }

    /**
     * Outcome of {@link #claimOrAwaitCached}: either an already-published result (cache hit), or a
     * fresh owner marker for a claim this call just won. Exactly one of the two fields is non-null.
     */
    record ClaimOutcome(IdempotencyOutput cachedResult, String ownerMarker) {

        static <T extends Serializable> ClaimOutcome hit(IdempotencyOutput cachedResult) {
            return new ClaimOutcome(cachedResult, null);
        }

        static <T extends Serializable> ClaimOutcome won(String ownerMarker) {
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
     * Converts a cached {@link IdempotencyOutput} back into either the original failure or the
     * original {@code T} value.
     * <p>
     * If the cached output carries an exception, an {@link FailedOutCome} describing it is
     * returned as-is (it is not thrown): callers are responsible for detecting this case (e.g. via
     * {@code instanceof}) and deciding how to react to a replayed failure. Otherwise, the raw JSON
     * value is converted back to a concrete type.
     * <p>
     * When the caller supplies {@code resultType}, it is used as the deserialization target instead
     * of the type recorded at serialization time: the registered key only identifies the erased
     * {@link Class} (e.g. {@code java.util.ArrayList}), which loses a parameterized result's
     * element type, so relying on it here would deserialize collection elements as raw {@code Map}s.
     *
     * @param output     the cached idempotency output to convert
     * @return either an {@link FailedOutCome} (replayed failure) or the deserialized result
     *         value (replayed success)
     */
    private Object unwrap(IdempotencyOutput output) {
        if (output.exception() != null) {
            return new FailedOutCome(output.exception().message(), output.exception().statusCode());
        }
        try {
            return output.unwrap();
        } catch (IllegalArgumentException e) {
            throw new IdempotencyPersistenceException(
                    "Failed to resolve idempotency result type: " + output.typename(), e);
        }
    }

    /**
     * Decodes a raw cache entry into an {@link IdempotencyOutput}.
     *
     * @return the decoded output — pending, successful or failed — or {@code null} if the entry is
     *         absent, empty or cannot be interpreted (e.g. corrupted or written by an older format)
     */
    private IdempotencyOutput decode(String cached, String cacheKey) {
        try {
            if (StringUtils.isEmpty(cached)) {
                return null;
            }
            JsonNode node = OBJECT_MAPPER.readTree(cached);
            if (node == null || node.isNull() || !node.isObject()) {
                return null;
            }
            JsonNode pendingNode = node.get("pending");
            if (pendingNode != null && pendingNode.booleanValue()) {
                JsonNode claimIdNode = node.get("claimId");
                String claimId = claimIdNode == null || claimIdNode.isNull() ? null : claimIdNode.asString();
                return IdempotencyOutput.pending(claimId);
            }
            JsonNode exceptionNode = node.get("exception");
            if (exceptionNode != null && !exceptionNode.isNull()) {
                return new IdempotencyOutput(false, null, null, null, decodeException(exceptionNode));
            }
            JsonNode valueNode = node.get("value");
            JsonNode typenameNode = node.get("__typename");
            if (valueNode != null && typenameNode != null && !typenameNode.isNull()) {
                return IdempotencyOutput.success(valueNode, typenameNode.asString());
            }
            return null;
        } catch (RuntimeException e) {
            log.warn("Failed to decode idempotency result from cache for key={}: {}", sanitizeForLog(cacheKey),
                    e.getMessage());
            return null;
        }
    }

    private static FailedOutCome decodeException(JsonNode exceptionNode) {
        JsonNode messageNode = exceptionNode.get("message");
        JsonNode httpErrorCodeNode = exceptionNode.get("statusCode");
        String message = messageNode == null || messageNode.isNull() ? null : messageNode.asString();
        int httpErrorCode = httpErrorCodeNode == null || httpErrorCodeNode.isNull() ? 0
                : httpErrorCodeNode.asInt();
        return new FailedOutCome(message, httpErrorCode);
    }

    /**
     * Serializes an {@link IdempotencyOutput} for storage in the key-value store.
     *
     * @throws IdempotencyPersistenceException if the output cannot be serialized; never returns
     *         {@code null}, which the store would interpret as a deletion
     */
    private static String serialize(IdempotencyOutput result) {
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
