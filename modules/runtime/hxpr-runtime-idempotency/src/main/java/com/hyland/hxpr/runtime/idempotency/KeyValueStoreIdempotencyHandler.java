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
package com.hyland.hxpr.runtime.idempotency;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.runtime.kv.KeyValueStore;
import com.hyland.openarch.runtime.kv.KeyValueService;

/**
 * Implementation of {@link IdempotencyHandler} that stores results in a {@link KeyValueService}
 * for persistent idempotency across requests.
 * <p>
 * Results are serialized to bytes and stored with an expiration TTL to prevent unbounded cache
 * growth. The cache key is derived from the idempotency key and a namespace prefix.
 * <p>
 * At-most-once execution is enforced by atomically claiming the cache key with
 * {@link KeyValueStore#compareAndSet(String, byte[], byte[], long)} before the operation runs: a
 * leased, per-claim owner marker (a unique token generated for that claim attempt) is CAS-written
 * in place of a plain read-then-write, so concurrent callers racing on the same key cannot both
 * observe a miss and both execute the operation. Callers that lose the race wait, polling for
 * either the winner's published result or the lease to expire, and throw
 * {@link IdempotencyClaimTimeoutException} if neither happens in time. Because each claim owns a
 * unique marker, a caller whose lease has already expired and been reclaimed by someone else can
 * never mistake the new claimant's marker for its own, so it can safely detect and discard a stale
 * outcome instead of overwriting the current claimant's state. The lease should be sized to
 * comfortably exceed the expected duration of the guarded operation.
 *
 * @since 12.0
 */
public final class KeyValueStoreIdempotencyHandler implements IdempotencyHandler {

    private static final Logger log = LogManager.getLogger(KeyValueStoreIdempotencyHandler.class);

    /**
     * Prefix shared by every in-progress claim marker. Each claim attempt appends a unique random
     * token to this prefix so that concurrent/successive claims on the same key never share the
     * same marker value, allowing {@link #finalizeResult} to detect whether it still owns the
     * claim before publishing a result. The prefix is chosen to never collide with a
     * Java-serialized {@link IdempotencyResult} (which always starts with the serialization stream
     * magic bytes) or with arbitrary/corrupted cache content.
     */
    private static final byte[] IN_PROGRESS_PREFIX = "HXPR-IDEMPOTENCY-CLAIM-V1:".getBytes(StandardCharsets.UTF_8);

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
    public <T extends Serializable> T execute(IdempotencyKey key, IdempotentOperation<T> operation) {
        return executeWithOutcome(key, operation).value();
    }

    @Override
    public <T extends Serializable> Outcome<T> executeWithOutcome(IdempotencyKey key,
            IdempotentOperation<T> operation) {
        String cacheKey = getCacheKey(key);

        ClaimOutcome<T> claim = claimOrAwaitCached(cacheKey, key.value());
        if (claim.cachedResult() != null) {
            log.debug("Idempotency cache hit for key={}", key.value());
            return new Outcome<>(claim.cachedResult().unwrap(), true);
        }

        T result = runAndFinalize(cacheKey, key, claim.ownerMarker(), operation);
        return new Outcome<>(result, false);
    }

    /**
     * Runs {@code operation}, which was just granted the claim identified by {@code ownerMarker},
     * and finalizes the cache entry with its outcome (success or failure).
     */
    private <T extends Serializable> T runAndFinalize(String cacheKey, IdempotencyKey key, byte[] ownerMarker,
            IdempotentOperation<T> operation) {
        try {
            T result = operation.execute();
            IdempotencyResult<T> successResult = IdempotencyResult.success(result);
            try {
                finalizeResult(cacheKey, ownerMarker, successResult);
            } catch (IdempotencyPersistenceException e) {
                log.error("Idempotency result for key={} could not be persisted; at-most-once is not "
                        + "guaranteed for this call", key.value(), e);
                // still return the result: the operation itself succeeded, we only failed to record it.
            }
            log.debug("Idempotency cache stored success for key={}", key.value());
            return result;
        } catch (RuntimeException e) {
            IdempotencyResult<T> failureResult = IdempotencyResult.failure(e);
            finalizeResult(cacheKey, ownerMarker, failureResult);
            log.debug("Idempotency cache stored failure for key={}", key.value());
            throw e;
        } catch (Exception e) {
            RuntimeException wrapped = new RuntimeException("Operation failed", e);
            IdempotencyResult<T> failureResult = IdempotencyResult.failure(wrapped);
            finalizeResult(cacheKey, ownerMarker, failureResult);
            log.debug("Idempotency cache stored wrapped failure for key={}", key.value());
            throw wrapped;
        }
    }


    @Override
    public boolean isCached(IdempotencyKey key) {
        String cacheKey = getCacheKey(key);
        byte[] current = keyValueStore.get(cacheKey);
        return current != null && !isInProgressMarker(current) && decode(current, cacheKey) != null;
    }

    @Override
    public boolean evict(IdempotencyKey key) {
        String cacheKey = getCacheKey(key);
        byte[] current = keyValueStore.get(cacheKey);
        if (current != null && !isInProgressMarker(current) && decode(current, cacheKey) != null) {
            return keyValueStore.compareAndSet(cacheKey, current, null);
        }
        return false;
    }

    private String getCacheKey(IdempotencyKey key) {
        return namespace + key.value();
    }

    /**
     * Atomically claims {@code cacheKey} for exclusive execution, or returns the already-published
     * result if one exists.
     * <p>
     * Returns a {@link ClaimOutcome} carrying either the already-published {@link IdempotencyResult}
     * (cache hit), or a fresh, unique owner marker when this call won the claim — the caller must
     * then run the operation and pass that owner marker to {@link #finalizeResult}. If another
     * caller currently holds the claim, this method waits (bounded by the lease duration plus a
     * small buffer) for it to either publish a result or have its lease expire, throwing
     * {@link IdempotencyClaimTimeoutException} if neither happens in time.
     */
    private <T extends Serializable> ClaimOutcome<T> claimOrAwaitCached(String cacheKey, String keyValue) {
        byte[] ownerMarker = newClaimMarker();
        long deadline = System.currentTimeMillis() + leaseSeconds * 1000L + CLAIM_WAIT_BUFFER_MILLIS;
        byte[] current = keyValueStore.get(cacheKey);
        while (true) {
            if (current == null) {
                if (keyValueStore.compareAndSet(cacheKey, (byte[]) null, ownerMarker, leaseSeconds)) {
                    return ClaimOutcome.won(ownerMarker);
                }
            } else if (isInProgressMarker(current)) {
                if (System.currentTimeMillis() >= deadline) {
                    throw new IdempotencyClaimTimeoutException(
                            "Timed out waiting for a concurrent idempotent operation to complete for key="
                                    + keyValue);
                }
                sleepQuietly(CLAIM_POLL_INTERVAL_MILLIS);
            } else {
                IdempotencyResult<T> cachedResult = decode(current, cacheKey);
                if (cachedResult != null) {
                    return ClaimOutcome.hit(cachedResult);
                }
                // Neither a valid result nor an in-progress marker (e.g. corrupted data): reclaim the slot atomically.
                if (keyValueStore.compareAndSet(cacheKey, current, ownerMarker, leaseSeconds)) {
                    return ClaimOutcome.won(ownerMarker);
                }
            }
            current = keyValueStore.get(cacheKey);
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
    private <T extends Serializable> void finalizeResult(String cacheKey, byte[] ownerMarker,
            IdempotencyResult<T> result) {
        byte[] serialized = serialize(cacheKey, result);
        boolean stored;
        try {
            stored = keyValueStore.compareAndSet(cacheKey, ownerMarker, serialized, ttlSeconds);
        } catch (RuntimeException e) {
            throw new IdempotencyPersistenceException("Failed to persist idempotency result for key=" + cacheKey, e);
        }
        if (!stored) {
            log.warn("Idempotency claim for key={} was reclaimed by another caller before this result could be "
                    + "finalized; discarding this outcome (the lease was too short for the operation's actual "
                    + "duration)", cacheKey);
            // Do NOT put(): the current value belongs to whoever reclaimed the key.
        }
    }

    private static boolean isInProgressMarker(byte[] value) {
        return value != null && value.length >= IN_PROGRESS_PREFIX.length
                && Arrays.equals(value, 0, IN_PROGRESS_PREFIX.length, IN_PROGRESS_PREFIX, 0, IN_PROGRESS_PREFIX.length);
    }

    private static byte[] newClaimMarker() {
        byte[] token = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        byte[] marker = Arrays.copyOf(IN_PROGRESS_PREFIX, IN_PROGRESS_PREFIX.length + token.length);
        System.arraycopy(token, 0, marker, IN_PROGRESS_PREFIX.length, token.length);
        return marker;
    }

    /**
     * Outcome of {@link #claimOrAwaitCached}: either an already-published result (cache hit), or a
     * fresh owner marker for a claim this call just won. Exactly one of the two fields is non-null.
     */
    private record ClaimOutcome<T extends Serializable>(IdempotencyResult<T> cachedResult, byte[] ownerMarker) {

        static <T extends Serializable> ClaimOutcome<T> hit(IdempotencyResult<T> cachedResult) {
            return new ClaimOutcome<>(cachedResult, null);
        }

        static <T extends Serializable> ClaimOutcome<T> won(byte[] ownerMarker) {
            return new ClaimOutcome<>(null, ownerMarker);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ClaimOutcome<?> other)) {
                return false;
            }
            return Objects.equals(cachedResult, other.cachedResult) && Arrays.equals(ownerMarker, other.ownerMarker);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cachedResult, Arrays.hashCode(ownerMarker));
        }

        @Override
        public String toString() {
            return "ClaimOutcome[cachedResult=" + cachedResult + ", ownerMarker=" + Arrays.toString(ownerMarker) + "]";
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdempotencyClaimTimeoutException("Interrupted while waiting for an idempotency claim", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Serializable> IdempotencyResult<T> decode(byte[] cached, String cacheKey) {
        try {
            if (cached == null || cached.length == 0) {
                return null;
            }
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(cached))) {
                return (IdempotencyResult<T>) ois.readObject();
            }
        } catch (Exception e) {
            log.warn("Failed to decode idempotency result from cache for key={}: {}", cacheKey, e.getMessage());
            return null;
        }
    }

    private <T extends Serializable> byte[] serialize(String cacheKey, IdempotencyResult<T> result) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(result);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("Failed to store idempotency result in cache for key={}: {}", cacheKey, e.getMessage());
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }
    }

    final class IdempotencyPersistenceException extends RuntimeException {
        IdempotencyPersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
