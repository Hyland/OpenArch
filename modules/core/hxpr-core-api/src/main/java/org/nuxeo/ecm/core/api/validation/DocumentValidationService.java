/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */

package org.nuxeo.ecm.core.api.validation;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.ValuesVisitor;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;

/**
 * Provides a way to validate {@link DocumentModel} according to their schema's constraints.
 *
 * @since 7.1
 */
public interface DocumentValidationService {

    String CTX_MAP_KEY = "DocumentValidationService.Forcing";

    String CTX_CREATEDOC = "createDocument";

    String CTX_SAVEDOC = "saveDocument";

    public enum Forcing {
        TURN_ON, TURN_OFF, USUAL;
    }

    /**
     * To activate validation in some context, for example "CoreSession.saveDocument", you have to contribute to
     * component "org.nuxeo.ecm.core.api.DocumentValidationService" with extension point "activations".
     * <p>
     * Example :
     * </p>
     *
     * <pre>
     * {@code
     * <extension target="org.nuxeo.ecm.core.api.DocumentValidationService" point="activations">
     *   <validation context="CoreSession.saveDocument" activated="false" />
     * </extension>
     * }
     * </pre>
     * <p>
     * Here are some available context :
     * </p>
     * <ul>
     * <li>CoreSession.createDocument</li>
     * <li>CoreSession.saveDocument</li>
     * </ul>
     *
     * @param context A string representation of the context, where validation service should be activated.
     * @param contextMap if not null, search forcing flag in the context map. see {@link Forcing} for values and
     *            {@link #CTX_MAP_KEY} for the key.
     * @return true if validation is activated in the specified context, false otherwise.
     * @since 7.1
     */
    boolean isActivated(String context, Map<String, Serializable> contextMap);

    /**
     * Validates a {@link DocumentModel}. Including no dirty properties.
     *
     * @since 7.1
     */
    DocumentValidationReport validate(DocumentModel document);

    /** Gets the validation visitor that will add to the given {@code violations}. */
    ValuesVisitor getValidationVisitor(List<ValidationViolation> violations);

}
