/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core;

import static jakarta.servlet.http.HttpServletResponse.SC_CONFLICT;
import static com.hyland.openarch.ecm.core.schema.MixinNames.SYS_HAS_EMBEDDINGS;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_EMBEDDINGS;

import java.util.Collection;
import java.util.List;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.ecm.core.api.validation.DocumentValidationException;
import com.hyland.openarch.ecm.core.api.validation.DocumentValidationReport;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;

/**
  * Centralized policies for SysEmbeddings document type related rules.
  * <p>
  * Naming logic split intentionally:
  * </p>
  * <ul>
  *   <li>{@link #handleEmbeddingsNamePolicy(InternalDocument, String, boolean)} is used at creation time: it may
  *   auto-prefix when enforcement is disabled.</li>
  *   <li>{@link #enforceEmbeddingsNameOrThrow(InternalDocument, String)} is used at rename/move time: it never auto-fixes,
  *   it only validates (invalid names are rejected).</li>
  * </ul>
  * <p>
  * This keeps creation ergonomics (auto-fix) while ensuring explicit user intent during rename operations.
  * </p>
  */
public final class SysEmbeddingsPolicies {

    public static final String EMBEDDINGS_NAME_PREFIX = "_e_";

    private SysEmbeddingsPolicies() {
        // utility
    }

    /**
     * Checks a mixin update and enforces that the {@code SysHasEmbeddings} mixin cannot be removed when the document
     * still has {@code SysEmbeddings} children. This is a no-op if the mixin is not being removed.
     */
    public static void enforceHasEmbeddingsMixinUpdateOrThrow(InternalDocument doc, Collection<String> newMixins) {
        boolean wasHas = doc.hasMixin(SYS_HAS_EMBEDDINGS);
        boolean willHas = newMixins != null && newMixins.contains(SYS_HAS_EMBEDDINGS);
        if (wasHas && !willHas) {
            // If removing SysHasEmbeddings, ensure there are no SysEmbeddings children
            for (InternalDocument child : doc.getChildren()) {
                if (isOfEmbeddingsType(child)) {
                    throw new RepositoryException("Cannot remove SysHasEmbeddings: SysEmbeddings children exist",
                            SC_CONFLICT);
                }
            }
        }
    }

    /**
     * Validates or auto-prefixes SysEmbeddings document names based on enforcement mode. No-op for non-embeddings
     * documents.
     *
     * @param doc the document being processed
     * @param originalName the original name to process
     * @param enforce if true, validates and throws; if false, auto-prefixes when missing
     * @return the original name (if valid/enforce mode) or prefixed version (if auto-fix mode)
     */
    public static String handleEmbeddingsNamePolicy(InternalDocument doc, String originalName, boolean enforce) {
        if (!isOfEmbeddingsType(doc)) {
            return originalName;
        }
        if (enforce) {
            validateEmbeddingsNamePrefix(originalName);
            return originalName;
        }
        return ensureEmbeddingsNamePrefix(originalName);
    }

    /**
     * Enforces SysEmbeddings document name prefix. No-op for non-embeddings documents.
     *
     * @param doc the document to check
     * @param name the name to validate
     * @throws DocumentValidationException if name is invalid for SysEmbeddings
     */
    public static void enforceEmbeddingsNameOrThrow(InternalDocument doc, String name) {
        if (!isOfEmbeddingsType(doc)) {
            return;
        }
        validateEmbeddingsNamePrefix(name);
    }

    /**
     * Validates that SysEmbeddings creation parent has required mixin. No-op for non-embeddings documents.
     *
     * @param doc the document being created
     * @throws DocumentValidationException if parent lacks SysHasEmbeddings mixin for SysEmbeddings creation
     */
    public static void enforceEmbeddingsParentMixinOrThrow(InternalDocument doc) {
        if (!isOfEmbeddingsType(doc)) {
            return;
        }
        InternalDocument parent = doc.getParent();
        if (hasEmbeddings(parent)) {
            return;
        }
        String parentId = parent == null ? null : parent.getId();
        ValidationViolation v = SysEmbeddingsViolation.ofParentMissingMixin(parentId, SYS_HAS_EMBEDDINGS);
        throw new DocumentValidationException(new DocumentValidationReport(List.of(v)));
    }

    /**
     * Ensures a name has the required embeddings prefix, adding it if missing.
     *
     * @param name the name to ensure has the embeddings prefix
     * @return the name with the embeddings prefix if missing
     */
    public static String ensureEmbeddingsNamePrefix(String name) {
        if (hasValidEmbeddingsPrefix(name)) {
            return name;
        }
        return EMBEDDINGS_NAME_PREFIX + name;
    }

    /**
     * Extracts the type part of a SysEmbeddings document name.
     *
     * @param embeddingDocName the SysEmbeddings document name
     * @return the type part of the name, starting after the prefix
     */
    public static String getEmbeddingsTypeFromName(String embeddingDocName) {
        if (hasValidEmbeddingsPrefix(embeddingDocName)) {
            return embeddingDocName.substring(EMBEDDINGS_NAME_PREFIX.length());
        } else {
            throw new RepositoryException("Invalid embeddings document name: " + embeddingDocName);
        }
    }

    // ------------------------------------------------------
    // Internal helper methods (kept private / bottom of class)
    // ------------------------------------------------------

    /** Returns true if the given document is of the SysEmbeddings type. */
    private static boolean isOfEmbeddingsType(InternalDocument doc) {
        return doc != null && SYS_EMBEDDINGS.equals(doc.getType().getName());
    }

    /** Returns true if the given document has the {@code SysHasEmbeddings} mixin. */
    private static boolean hasEmbeddings(InternalDocument doc) {
        return doc != null && doc.hasMixin(SYS_HAS_EMBEDDINGS);
    }

    /** Returns true if the given name is non-null and already prefixed for embeddings. */
    private static boolean hasValidEmbeddingsPrefix(String name) {
        return name != null && name.startsWith(EMBEDDINGS_NAME_PREFIX);
    }

    /** Validates that a SysEmbeddings document name follows the required prefix. */
    private static void validateEmbeddingsNamePrefix(String name) {
        if (hasValidEmbeddingsPrefix(name)) {
            return;
        }
        ValidationViolation v = SysEmbeddingsViolation.ofNamePrefix(name, EMBEDDINGS_NAME_PREFIX);
        throw new DocumentValidationException(new DocumentValidationReport(List.of(v)));
    }

}
