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
package org.nuxeo.ecm.core.api.query;

import java.util.Map;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;

/**
 * Parameters that can be passed when executing a query.
 *
 * @param parameters the values for the query's {@code :name} parameters, bound when it is parsed
 */
public record QueryParameters(NuxeoPrincipal principal, long limit, long offset, Map<String, Object> parameters) {

    public QueryParameters {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public QueryParameters(NuxeoPrincipal principal, long limit, long offset) {
        this(principal, limit, offset, Map.of());
    }

}
