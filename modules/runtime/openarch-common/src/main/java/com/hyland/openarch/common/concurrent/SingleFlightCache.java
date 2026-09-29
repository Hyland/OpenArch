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
package com.hyland.openarch.common.concurrent;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * Coalesces concurrent callers for the same key into one {@code loader} call, preventing a cache stampede. Entries are
 * invalidated right after use, so this never lingers as a second cache. A TTL backstops that cleanup in case a caller
 * dies before it runs - see {@link #SingleFlightCache(Duration)}.
 * <p>
 * Uses Guava, not Caffeine: Guava's {@code Cache.get(key, Callable)} coalesces failed loads too, not just successful
 * ones.
 *
 * @param <V> the type of value produced by the loader
 */
public class SingleFlightCache<V> {

    /**
     * Safety-net TTL used when none is given. Must comfortably exceed the slowest expected loader: it is a leak guard,
     * not a coalescing window, and expiring mid-flight only costs a duplicate load, never correctness.
     */
    public static final Duration DEFAULT_MAX_FLIGHT_DURATION = Duration.ofMinutes(5);

    // Package-private (not private) so SingleFlightCacheTest can construct exact stale-vs-current
    // generation scenarios directly, without relying on real thread timing to reproduce them.
    final Cache<String, Flight<V>> inFlight;

    /**
     * Creates a cache whose flights are evicted after {@link #DEFAULT_MAX_FLIGHT_DURATION} at the latest.
     */
    public SingleFlightCache() {
        this(DEFAULT_MAX_FLIGHT_DURATION);
    }

    /**
     * Creates a cache with an explicit upper bound on how long a flight may linger. Entries are normally removed as
     * soon as the loader returns; this TTL only guards against a caller dying between the load and its cleanup, which
     * would otherwise pin the entry forever. Pick a value well above the slowest expected loader - use a longer one
     * where loads are slow, as expiring while a load is still running merely lets a second caller start a duplicate
     * load.
     *
     * @param maxFlightDuration how long an entry may survive after being written
     */
    public SingleFlightCache(Duration maxFlightDuration) {
        inFlight = CacheBuilder.newBuilder().expireAfterWrite(maxFlightDuration).build();
    }

    /**
     * Runs {@code loader} for {@code key}, coalescing concurrent callers into one invocation. A null key bypasses
     * coalescing entirely (Guava's cache rejects null keys, unlike the durable OpenArch caches this sits alongside) and
     * just calls {@code loader} directly.
     */
    public V get(String key, Supplier<V> loader) {
        if (key == null) {
            return loader.get();
        }
        Flight<V> flight;
        try {
            flight = inFlight.get(key, () -> new Flight<>(loader.get()));
        } catch (ExecutionException | UncheckedExecutionException e) {
            // Guava already removes its own failed placeholder by reference before propagating
            // this exception, so there's nothing left for us to clean up here.
            throw unwrap(e);
        }
        release(key, flight);
        return flight.value;
    }

    /**
     * Removes {@code key}'s entry - but only if it still holds THIS {@code flight}. A caller whose own generation has
     * already been replaced by a newer one (e.g. a follower whose cleanup runs late) must not evict that newer
     * generation.
     */
    void release(String key, Flight<V> flight) {
        inFlight.asMap().remove(key, flight);
    }

    // Guava always wraps the loader's exception (ExecutionException/UncheckedExecutionException); unwrap
    // it so callers see the original exception, exactly as if there were no cache in front of them.
    private static RuntimeException unwrap(Exception e) {
        Throwable cause = e.getCause();
        return cause instanceof RuntimeException re ? re : new IllegalStateException(cause);
    }

    /**
     * Wraps a loaded value with a unique identity per attempt, so a stale cleanup can be told apart from the current
     * one by reference, regardless of whether the values themselves are equal.
     */
    static final class Flight<V> {
        final V value;

        Flight(V value) {
            this.value = value;
        }
    }
}
