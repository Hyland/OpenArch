/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.storage;

import java.util.IdentityHashMap;
import java.util.Map;

import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.Reference;

/**
 * What the {@link QueryOptimizer} worked out about the nodes of a query, kept beside the tree rather than inside it.
 * <p>
 * This is derived data, not syntax: the same tree optimized against a different schema would be annotated differently.
 * Keeping it here is what lets the tree itself stay immutable, and makes it obvious that a tree which never went
 * through the optimizer simply has no annotations.
 * <p>
 * Nodes are keyed by identity, since annotations describe an occurrence: two equal references in different places can
 * be renamed differently.
 *
 * @since 12.0
 */
public class QueryAnnotations {

    /** No annotations at all, for a query that did not go through the optimizer. */
    public static final QueryAnnotations NONE = new QueryAnnotations();

    /**
     * Info about a correlated wildcard prefix: the prefix, and how many references shared it.
     *
     * @since 9.3
     */
    public record PrefixInfo(String prefix, int count) {

        public static final PrefixInfo EMPTY = new PrefixInfo("", 0);
    }

    protected final Map<Operand, PrefixInfo> prefixes = new IdentityHashMap<>();

    protected final Map<Reference, String> originalNames = new IdentityHashMap<>();

    /** The prefix info computed for a node, or {@code null} if none was. */
    public PrefixInfo getPrefixInfo(Operand node) {
        return prefixes.get(node);
    }

    /** The common prefix of a node's references, or the empty string if they have none. */
    public String getPrefix(Operand node) {
        PrefixInfo info = prefixes.get(node);
        return info == null ? "" : info.prefix();
    }

    /** How many references under a node share its common prefix, or zero if they share none. */
    public int getCount(Operand node) {
        PrefixInfo info = prefixes.get(node);
        return info == null ? 0 : info.count();
    }

    public void setPrefixInfo(Operand node, PrefixInfo info) {
        if (info == null) {
            prefixes.remove(node);
        } else {
            prefixes.put(node, info);
        }
    }

    /**
     * The name a reference had before the optimizer renamed it, or {@code null} if it was not renamed.
     */
    public String getOriginalName(Reference reference) {
        return originalNames.get(reference);
    }

    public void setOriginalName(Reference reference, String originalName) {
        originalNames.put(reference, originalName);
    }

}
