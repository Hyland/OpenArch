/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.query.spec;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import org.nuxeo.rest.query.io.DocumentNamedQueryDTO;
import org.nuxeo.rest.query.io.QuickFilterDTO;
import org.nuxeo.rest.query.io.WhereClauseDTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Representation of a named query definition, to be used in conjunction with {@link DocumentNamedQueryDTO}.
 */
@Schema(name = "NamedQueryDefinition", title = "Named Query Definition", description = "Represents a named query definition.")
public record NamedQueryDefinition(String name, //
        @JsonInclude(Include.NON_EMPTY) List<String> aliases, //
        @JsonInclude(Include.NON_NULL) WhereClauseDTO whereClauseDefinition, //
        @JsonInclude(Include.NON_EMPTY) List<String> sort, //
        @JsonInclude(Include.NON_EMPTY) List<QuickFilterDTO> quickFilters) {

    public static NamedQueryDefinition of(String name, List<String> aliases, WhereClauseDTO whereClauseDefinition,
            List<String> sortInfos, List<QuickFilterDTO> quickFilters) {
        return new NamedQueryDefinition(name, aliases, whereClauseDefinition, sortInfos, quickFilters);
    }

    public static NamedQueryDefinition of(String name, WhereClauseDTO whereClauseDefinition, List<String> sortInfos) {
        return new NamedQueryDefinition(name, null, whereClauseDefinition, sortInfos, null);
    }

    public NamedQueryDefinition {
        if (aliases == null) {
            aliases = List.of();
        }
        if (sort == null) {
            sort = List.of();
        }
        if (quickFilters == null) {
            quickFilters = List.of();
        }
    }

    public QuickFilterDTO getQuickFilter(String name) {
        return quickFilters().stream().filter(f -> name.equals(f.name())).findAny().orElse(null);
    }

}
