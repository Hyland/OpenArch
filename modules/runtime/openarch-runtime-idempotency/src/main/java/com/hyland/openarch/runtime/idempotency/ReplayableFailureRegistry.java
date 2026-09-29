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

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extensible allow-list of exception types that can be faithfully rebuilt when a cached idempotent
 * failure is replayed.
 * <p>
 * A cached failure only carries the original exception's class name, message and HTTP status. That
 * is enough to rebuild a handful of exception types exactly; for every other type it is not, so the
 * replay degrades to a plain {@link RepositoryException} carrying the same message and status. Because
 * the global exception mapping answers from the status and the message, that fallback is
 * indistinguishable from the original response for the caller.
 * <p>
 * This module only knows how to rebuild {@link RepositoryException} itself out of the box. Modules
 * that define their own {@link RepositoryException} subclasses and want them to survive a replay
 * exactly must call {@link #register(Class, ReplayableFailureFactory)} once at
 * startup -- typically from a Spring singleton's constructor or a Nuxeo component's {@code start()} -- passing a
 * constructor reference for the type. Entries are registered as constructor lambdas rather than
 * resolved reflectively: the caller already has the {@link Class} object in hand, so compile-time
 * checking costs nothing and the cached class name coming back from the replay is never used to load
 * a class.
 * <h2>Types that should deliberately be left out of a registration</h2>
 * <ul>
 * <li>Exceptions whose handler dereferences state that cannot round-trip through the cache (e.g. a
 * validation report); a rebuilt instance would fail inside the handler and turn a replayed 4xx into a
 * 500.</li>
 * <li>Exceptions whose single constructor argument is an identifier rather than a message -- they
 * format the message themselves, so feeding the stored message back in would duplicate it.</li>
 * <li>Exceptions with no constructor able to carry a message.</li>
 * <li>Exceptions not extending {@link RepositoryException} -- {@link IdempotencyOutput} only captures
 * an HTTP status for {@link RepositoryException}, so these always replay as a 500 and there is
 * nothing to preserve.</li>
 * </ul>
 */
public final class ReplayableFailureRegistry {

    private static final Logger log = LoggerFactory.getLogger(ReplayableFailureRegistry.class);

    private static final ConcurrentMap<String, ReplayableFailureFactory> REGISTRY =
            new ConcurrentHashMap<>();

    static {
        register(RepositoryException.class, RepositoryException::new);
        register(DocumentNotFoundException.class,  (message, _) -> new DocumentNotFoundException(message));
        register(NullPointerException.class,  (message, _) -> new NullPointerException(message));
    }

    private ReplayableFailureRegistry() {
        // utility class
    }

    /**
     * Registers {@code exceptionClass} so that a replayed failure carrying its name is rebuilt via
     * {@code factory} instead of falling back to a plain {@link RepositoryException}.
     * <p>
     * Safe to call multiple times for the same class (e.g. from a Spring singleton's constructor);
     * later registrations simply replace earlier ones for that class name.
     *
     * @param exceptionClass the {@link RepositoryException} subclass to allow-list
     * @param factory        rebuilds an instance of {@code exceptionClass} from a message and status
     */
    public static void register(Class<? extends Exception> exceptionClass,
            ReplayableFailureFactory factory) {
        Objects.requireNonNull(exceptionClass, "exceptionClass cannot be null");
        Objects.requireNonNull(factory, "factory cannot be null");
        REGISTRY.put(exceptionClass.getName(), factory);
    }

    /**
     * Rebuilds the exception identified by {@code className}, or returns a {@link RepositoryException}
     * carrying the same message and status when the type is not allow-listed or cannot be rebuilt
     * faithfully.
     *
     * @param className the original exception's fully qualified class name, possibly null
     * @param message   the original exception message
     * @param status    the HTTP status code the original exception carried
     * @return an exception to throw in place of the original one, never null
     */
    public static Exception rebuild(String className, String message, int status) {
        ReplayableFailureFactory factory = className == null ? null : REGISTRY.get(className);
        if (factory == null) {
            return new RepositoryException(message, status);
        }
        Exception rebuilt;
        try {
            rebuilt = factory.create(message, status);
        } catch (RuntimeException e) {
            log.warn("Could not rebuild replayed failure {}, falling back to RepositoryException", className, e);
            return new RepositoryException(message, status);
        }
        if (rebuilt instanceof RepositoryException repositoryException && repositoryException.getStatusCode() != status) {
            // A registered constructor that pins a different status would silently change the HTTP
            // contract of the replay, so the faithful-but-generic answer is preferred.
            log.warn("Rebuilt replayed failure {} carries status {} instead of {}, falling back to RepositoryException",
                    className, repositoryException.getStatusCode(), status);
            return new RepositoryException(message, status);
        }
        return rebuilt;
    }

    /**
     * Rebuilds an exception from the data preserved in the idempotency cache.
     */
    @FunctionalInterface
    public interface ReplayableFailureFactory {

        /**
         * @param message the original exception message
         * @param status  the original HTTP status code
         * @return a new exception instance equivalent to the original one
         */
        Exception create(String message, int status);
    }
}
