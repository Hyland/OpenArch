/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.document;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Document ancestors representation, useful for json serialization and swagger schema generation with the ReST API.
 */
@Schema(name = "DocumentAncestors", description = "Represents a document's ancestors list.")
public class DocumentAncestorsDTO {

    @JsonInclude(Include.NON_EMPTY)
    @JsonProperty("ancestors")
    protected List<DocumentDTO> ancestors;

    public List<DocumentDTO> getAncestors() {
        return ancestors;
    }

    public void setAncestors(List<DocumentDTO> ancestors) {
        this.ancestors = ancestors;
    }

}
