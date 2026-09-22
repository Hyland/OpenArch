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

import com.hyland.openarch.runtime.api.Framework;

/**
 * Helper class to run code with an unrestricted session.
 * <p>
 * The caller must implement the {@link #run} method, and call {@link #runUnrestricted}.
 *
 * @author Florent Guillaume
 */
public abstract class UnrestrictedSessionRunner {

    protected String originatingUserId;

    protected CoreSession session;

    protected final boolean sessionIsAlreadyUnrestricted;

    protected final String repositoryName;

    /** True if a call to {@link #runUnrestricted} is in progress. */
    protected boolean isUnrestricted;

    /**
     * Constructs a {@link UnrestrictedSessionRunner} given an existing session (which may or may not be already
     * unrestricted).
     * <p>
     * Originating user is taken on given session.
     *
     * @param session the available session
     */
    protected UnrestrictedSessionRunner(CoreSession session) {
        this.session = session;
        sessionIsAlreadyUnrestricted = checkUnrestricted(session);
        if (sessionIsAlreadyUnrestricted) {
            repositoryName = null;
        } else {
            repositoryName = session.getRepositoryName();
        }
        NuxeoPrincipal pal = session.getPrincipal();
        if (pal != null) {
            originatingUserId = pal.getId();
        }
    }

    /**
     * Constructs a {@link UnrestrictedSessionRunner} given a repository name.
     *
     * @param repositoryName the repository name
     */
    protected UnrestrictedSessionRunner(String repositoryName) {
        session = null;
        sessionIsAlreadyUnrestricted = false;
        this.repositoryName = repositoryName;
    }

    /**
     * Constructs a {@link UnrestrictedSessionRunner} given a repository name and an originating user name.
     *
     * @param repositoryName the repository name
     * @param originatingUsername the user name behind the system user
     * @param originatingUserid the user id behind the system user
     */
    protected UnrestrictedSessionRunner(String repositoryName, NuxeoPrincipal originatingPrincipal) {
        session = null;
        sessionIsAlreadyUnrestricted = false;
        this.repositoryName = repositoryName;
        if (originatingPrincipal != null) {
            this.originatingUserId = originatingPrincipal.getId();
        }
    }

    protected boolean checkUnrestricted(CoreSession session) {
        return session.getPrincipal().isAdministrator();
    }

    /**
     * Calls the {@link #run()} method with an unrestricted {@link #session}. During this call, {@link #isUnrestricted}
     * is set to {@code true}.
     */
    public void runUnrestricted() {
        isUnrestricted = true;
        try {
            if (sessionIsAlreadyUnrestricted) {
                if (NuxeoPrincipal.isCurrentAdministrator()) {
                    run();
                } else {
                    // should be removed when login and session will be synchronized / NXP-27399
                    Framework.doPrivileged(this::run);
                }
                return;
            }

            CoreSession baseSession = session;
            try (var __ = Framework.loginSystem(originatingUserId)) {
                session = CoreInstance.getCoreSession(repositoryName);
                run();
            } finally {
                session = baseSession;
            }
        } finally {
            isUnrestricted = false;
        }
    }

    /**
     * This method will be called by {@link #runUnrestricted()} with {@link #session} available as an unrestricted
     * session.
     * <p>
     * It can also be called directly in which case the {@link #session} available will be the one passed to
     * {@code #UnrestrictedSessionRunner(CoreSession)}.
     */
    public abstract void run();

}
