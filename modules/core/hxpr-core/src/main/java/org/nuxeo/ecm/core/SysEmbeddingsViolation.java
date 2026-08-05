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
package org.nuxeo.ecm.core;

import java.util.List;

import org.nuxeo.ecm.core.api.DocumentConstants;
import org.nuxeo.ecm.core.schema.validation.violations.AbstractBaseValidationViolation;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;

/**
 * Validation violation for SysEmbeddings-related rules.
 */
public class SysEmbeddingsViolation extends AbstractBaseValidationViolation {

    public static final String NAME_PREFIX_KEY = "label.schema.application.violation.SysEmbeddingsViolation.namePrefix";

    public static final String PARENT_MISSING_MIXIN_KEY = "label.schema.application.violation.SysEmbeddingsViolation.parentMissing";

    public static final String HAS_EMBEDDING_CHILDREN_KEY = "label.schema.application.violation.SysEmbeddingsViolation.childrenExist";

    private SysEmbeddingsViolation(String messageKey, List<Object> parameters, Object invalidValue, String path,
            ValidationViolation.Type type) {
        super(messageKey, parameters, invalidValue, path, type);
    }

    // Simple factory helpers
    public static SysEmbeddingsViolation ofNamePrefix(String invalidName, String requiredPrefix) {
        return new SysEmbeddingsViolation(NAME_PREFIX_KEY, List.of(requiredPrefix), invalidName,
                DocumentConstants.SYS_NAME, ValidationViolation.Type.ERROR);
    }

    public static SysEmbeddingsViolation ofParentMissingMixin(String parentId, String mixin) {
        return new SysEmbeddingsViolation(PARENT_MISSING_MIXIN_KEY, List.of(mixin), parentId,
                DocumentConstants.SYS_PARENT_ID, ValidationViolation.Type.ERROR);
    }

    public static SysEmbeddingsViolation ofHasEmbeddingsChildren(String parentId) {
        return new SysEmbeddingsViolation(HAS_EMBEDDING_CHILDREN_KEY, List.of(), parentId,
                DocumentConstants.SYS_MIXIN_TYPES, ValidationViolation.Type.ERROR);
    }
}
