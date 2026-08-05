/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.api;

import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.mutable.MutableObject;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.runtime.api.Framework;

/**
 * The CoreInstance is the main access point to a CoreSession.
 */
public class CoreInstance {

    private CoreInstance() {
    }

    /**
     * Gets a {@link CoreSession} for the currently logged-in user.
     *
     * @param repositoryName the repository name, or {@code null} for the default repository
     * @return the session
     * @since 11.1
     */
    public static CoreSession getCoreSession(String repositoryName) {
        return getCoreSession(repositoryName, getPrincipal());
    }

    /**
     * Gets a {@link CoreSession} for a system user.
     *
     * @param repositoryName the repository name, or {@code null} for the default repository
     * @return the session
     * @since 11.1
     */
    public static CoreSession getCoreSessionSystem(String repositoryName) {
        return getCoreSession(repositoryName, new SystemPrincipal());
    }

    /**
     * Gets a {@link CoreSession} for a system user with an optional originating username.
     *
     * @param repositoryName the repository name, or {@code null} for the default repository
     * @param originatingUserId the originating userid to set on the SystemPrincipal
     * @return the session
     * @since 11.1
     */
    public static CoreSession getCoreSessionSystem(String repositoryName, String originatingUserId) {
        return getCoreSession(repositoryName, new SystemPrincipal(originatingUserId));
    }

    /**
     * Gets a {@link CoreSession} for the given principal.
     *
     * @param repositoryName the repository name, or {@code null} for the default repository
     * @param principal the principal
     * @return the session
     * @since 11.1
     */
    public static CoreSession getCoreSession(String repositoryName, NuxeoPrincipal principal) {
        if (repositoryName == null) {
            RepositoryManager repositoryManager = Framework.getService(RepositoryManager.class);
            repositoryName = repositoryManager.getDefaultRepositoryName();
        }
        return Framework.getService(CoreSessionService.class).createCoreSession(repositoryName, principal);
    }

    protected static NuxeoPrincipal getPrincipal() {
        NuxeoPrincipal nuxeoPrincipal = NuxeoPrincipal.getCurrent();
        if (nuxeoPrincipal != null) {
            return nuxeoPrincipal;
        } else {
            if (Framework.isTestModeSet()) {
                return new SystemPrincipal();
            } else {
                throw new NuxeoException(
                        "Cannot create a CoreSession outside a security context, " + " login() missing.");
            }
        }
    }

    /**
     * Runs the given {@link Function} with a system {@link CoreSession} while logged in as a system user.
     *
     * @param repositoryName the repository name for the {@link CoreSession}
     * @param function the function taking a system {@link CoreSession} and returning a result of type {@code <R>}
     * @param <R> the function return type
     * @return the result of the function
     * @since 8.4
     */
    public static <R> R doPrivileged(String repositoryName, Function<CoreSession, R> function) {
        MutableObject<R> result = new MutableObject<>();
        new UnrestrictedSessionRunner(repositoryName, NuxeoPrincipal.getCurrent()) {
            @Override
            public void run() {
                result.setValue(function.apply(session));
            }
        }.runUnrestricted();
        return result.getValue();
    }

    /**
     * Runs the given {@link Function} with a system {@link CoreSession} while logged in as a system user.
     *
     * @param session an existing session
     * @param function the function taking a system {@link CoreSession} and returning a result of type {@code <R>}
     * @param <R> the function return type
     * @return the result of the function
     * @since 8.4
     */
    public static <R> R doPrivileged(CoreSession session, Function<CoreSession, R> function) {
        MutableObject<R> result = new MutableObject<>();
        new UnrestrictedSessionRunner(session) {
            @Override
            public void run() {
                result.setValue(function.apply(session));
            }
        }.runUnrestricted();
        return result.getValue();
    }

    /**
     * Runs the given {@link Consumer} with a system {@link CoreSession} while logged in as a system user.
     *
     * @param repositoryName the repository name for the {@link CoreSession}
     * @param consumer the consumer taking a system {@link CoreSession}
     * @since 8.4
     */
    public static void doPrivileged(String repositoryName, Consumer<CoreSession> consumer) {
        new UnrestrictedSessionRunner(repositoryName, NuxeoPrincipal.getCurrent()) {
            @Override
            public void run() {
                consumer.accept(session);
            }
        }.runUnrestricted();
    }

    /**
     * Runs the given {@link Consumer} with a system {@link CoreSession} while logged in as a system user.
     *
     * @param session an existing session
     * @param consumer the consumer taking a system {@link CoreSession}
     * @since 8.4
     */
    public static void doPrivileged(CoreSession session, Consumer<CoreSession> consumer) {
        new UnrestrictedSessionRunner(session) {
            @Override
            public void run() {
                consumer.accept(session);
            }
        }.runUnrestricted();
    }

}
