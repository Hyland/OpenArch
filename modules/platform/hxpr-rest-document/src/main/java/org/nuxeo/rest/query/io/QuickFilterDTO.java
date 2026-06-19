/*
 * (C) Copyright 2021-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.rest.query.io;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import org.nuxeo.ecm.core.query.nxql.QuickFilter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Rest representation of a quick filter.
 */
@Schema(name = "QuickFilter", title = "Query Quick Filter", description = "Represents an advanced query quick filter definition.")
public record QuickFilterDTO(String name, //
        @JsonInclude(Include.NON_EMPTY) String clause, //
        @JsonInclude(Include.NON_EMPTY) List<String> sort) {

    public static QuickFilterDTO of(String name, String clause) {
        return new QuickFilterDTO(name, clause, null);
    }

    public static QuickFilterDTO of(String name, String clause, List<String> sort) {
        return new QuickFilterDTO(name, clause, sort);
    }

    public QuickFilterDTO {
        if (sort == null) {
            sort = List.of();
        }
    }

    public QuickFilter asQuickFilter() {
        return new QuickFilter(name(), clause(), DocumentQueryDTO.getSortInfos(sort()));
    }

}
