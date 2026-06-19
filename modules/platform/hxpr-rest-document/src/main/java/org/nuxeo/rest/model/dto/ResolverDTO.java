/*
 * (C) Copyright 2022-2024 Hyland (http://nuxeo.com/) and others.
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
 *     Saumit Mandal
 */
package org.nuxeo.rest.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@Schema(name = "Resolver", description = "Represents a resolver.")
public record ResolverDTO( //
        @JsonInclude(Include.NON_EMPTY) String name, //
        boolean validation, //
        @JsonInclude(Include.NON_EMPTY) String vocabulary, //
        @JsonInclude(Include.NON_EMPTY) String store, //
        @JsonInclude(Include.NON_EMPTY) String type, //
        @JsonInclude(Include.NON_EMPTY) String repository) implements BaseModelDTO {

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(Constants.NAME, this.name());
        map.put(Constants.VALIDATION, this.validation());
        if (this.vocabulary() != null)
            map.put(Constants.VOCABULARY, this.vocabulary());
        if (this.store() != null)
            map.put(Constants.STORE, this.store());
        if (this.type() != null)
            map.put(Constants.TYPE, this.type());
        if (this.repository() != null)
            map.put(Constants.REPOSITORY, this.repository());
        return map;
    }

    public static BaseModelDTO toDTO(Map<String, Object> model) {
        String name = null;
        boolean validation = true;
        String vocabulary = null;
        String store = null;
        String type = null;
        String repository = null;
        if (model.get(Constants.NAME) != null)
            name = (String) model.get(Constants.NAME);
        if (model.get(Constants.VALIDATION) != null)
            validation = Boolean.parseBoolean(model.get(Constants.VALIDATION).toString());
        if (model.get(Constants.VOCABULARY) != null)
            vocabulary = (String) model.get(Constants.VOCABULARY);
        if (model.get(Constants.STORE) != null)
            store = (String) model.get(Constants.STORE);
        if (model.get(Constants.TYPE) != null)
            type = (String) model.get(Constants.TYPE);
        if (model.get(Constants.REPOSITORY) != null)
            repository = (String) model.get(Constants.REPOSITORY);
        return new ResolverDTO(name, validation, vocabulary, store, type, repository);
    }
}
