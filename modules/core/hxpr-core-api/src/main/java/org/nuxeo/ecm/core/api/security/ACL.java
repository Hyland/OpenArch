/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.ecm.core.api.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * An ACL (Access Control List) is a list of ACEs (Access Control Entry).
 */
public interface ACL extends List<ACE>, Serializable, Cloneable {

    /**
     * Block the inheritance.
     *
     * @param userId the user blocking the inheritance
     * @return true if the ACL was changed.
     * @since 7.4
     */
    boolean blockInheritance(String userId);

    /**
     * Unblock the inheritance.
     *
     * @return true if the ACL was changed.
     * @since 7.4
     */
    boolean unblockInheritance();

    /**
     * Checks if the ACL contains an ACE blocking inheritance.
     */
    boolean isBlockingInheritance();

    /**
     * Add an ACE, except if it's already present in the ACL.
     * <p>
     * The ACE is added at the end of the ACL, except if the ACL contains a block in which case the ACE is added just
     * before the block.
     *
     * @return true if the ACL was changed.
     * @since 7.4
     */
    @Override
    boolean add(ACE ace);

    /**
     * Replace the {@code oldACE} with {@code newACE}, only if the {@code oldACE} exists.
     * <p>
     * The {@code newACE} keeps the same index as {@code oldACE}.
     *
     * @return true if the ACL was changed.
     * @since 7.4
     */
    boolean replace(ACE oldACE, ACE newACE);

    /**
     * Remove all ACEs for {@code username}.
     *
     * @return true if the ACL was changed.
     * @since 7.4
     */
    boolean removeByPrincipal(String username);

    /**
     * Returns a recursive copy of the ACL sharing no mutable substructure with the original.
     *
     * @return a copy
     */
    ACL clone();

    /**
     * Checks the access on the ACL for each set of the given permissions and principals.
     * <p>
     * This differs for an iterative check using {@link #getAccess(String principal, String permission)} in the order of
     * checks - so that in this case each ACE is fully checked against the given users and permissions before passing to
     * the next ACE.
     */
    Access getAccess(Collection<String> principals, Collection<String> permissions);

}
