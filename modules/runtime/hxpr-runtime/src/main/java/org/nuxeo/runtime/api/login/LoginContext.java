/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.runtime.api.login;

import java.security.Principal;
import java.util.Deque;
import java.util.LinkedList;

/**
 * An implementation of a login context just holding a principal.
 */
public class LoginContext implements AutoCloseable {

    /**
     * The thread-local principal stack. The top of the stack (last element) contains the current principal.
     *
     * @since 11.1
     */
    private static final ThreadLocal<Deque<Principal>> PRINCIPAL_STACK = ThreadLocal.withInitial(LinkedList::new);

    private final Principal principal;

    private boolean closed;

    public LoginContext(Principal principal) {
        this.principal = principal;
        pushPrincipal(principal);
    }

    public Principal getPrincipal() {
        return principal;
    }

    @Override
    public void close() {
        if (!closed) {
            popPrincipal();
            closed = true;
        }
    }

    /**
     * INTERNAL.
     *
     * @since 11.1
     */
    public static Deque<Principal> getPrincipalStack() {
        return PRINCIPAL_STACK.get();
    }

    /**
     * INTERNAL.
     *
     * @since 11.1
     */
    public static void clearPrincipalStack() {
        // removes the whole thread-local, so original stack object is untouched
        PRINCIPAL_STACK.remove();
    }

    /**
     * Pushes the principal to the current principal stack.
     *
     * @param principal the principal
     * @since 11.1
     */
    public static void pushPrincipal(Principal principal) {
        PRINCIPAL_STACK.get().addLast(principal);
    }

    /**
     * Pops the last principal from the current principal stack.
     *
     * @return the last principal, or {@code null} if the stack is empty
     * @since 11.1
     */
    public static Principal popPrincipal() {
        return PRINCIPAL_STACK.get().pollLast();
    }

    /**
     * Returns the last principal from the current principal stack.
     *
     * @return the last principal, or {@code null} if the stack is empty
     * @since 11.1
     */
    public static Principal getCurrentPrincipal() {
        return PRINCIPAL_STACK.get().peekLast();
    }

}
