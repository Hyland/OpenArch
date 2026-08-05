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
package org.nuxeo.rest.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@Schema(name = "Constraint", description = "Represents a constraint.")
public record ConstraintDTO( //
        @JsonInclude(Include.NON_DEFAULT) long minLength, //
        @JsonInclude(Include.NON_DEFAULT) long maxLength, //
        @JsonInclude(Include.NON_NULL) ResolverDTO resolver, //
        boolean nullable, //
        @JsonInclude(Include.NON_NULL) Object minInclusive, //
        @JsonInclude(Include.NON_NULL) Object minExclusive, //
        @JsonInclude(Include.NON_NULL) Object maxInclusive, //
        @JsonInclude(Include.NON_NULL) Object maxExclusive, //
        @JsonInclude(Include.NON_NULL) String pattern, //
        @JsonInclude(Include.NON_NULL) List<Object> enumeration) implements BaseModelDTO {

    private Object convertToLongOrDefault(Object data) {
        try {
            return Long.parseLong(data.toString());
        } catch (NumberFormatException e) {
            return data;
        }
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (this.minLength() > 0)
            map.put(Constants.MIN_LENGTH, this.minLength());
        if (this.maxLength() > 0)
            map.put(Constants.MAX_LENGTH, this.maxLength());
        if (this.minInclusive() != null)
            map.put(Constants.MIN_INCLUSIVE, convertToLongOrDefault(this.minInclusive()));
        if (this.minExclusive != null)
            map.put(Constants.MIN_EXCLUSIVE, convertToLongOrDefault(this.minExclusive()));
        if (this.maxInclusive() != null)
            map.put(Constants.MAX_INCLUSIVE, convertToLongOrDefault(this.maxInclusive()));
        if (this.maxExclusive() != null)
            map.put(Constants.MAX_EXCLUSIVE, convertToLongOrDefault(this.maxExclusive()));
        if (this.resolver() != null)
            map.put(Constants.RESOLVER, this.resolver().toMap());
        if (this.pattern() != null)
            map.put(Constants.PATTERN, this.pattern());
        if (this.enumeration() != null)
            map.put(Constants.ENUMERATION, this.enumeration());
        map.put(Constants.NULLABLE, this.nullable());
        return map;
    }

    public static BaseModelDTO toDTO(Map<String, Object> model) {
        if (model == null) {
            return null;
        } else {
            long minLength = 0, maxLength = 0;
            ResolverDTO resolver = null;
            boolean nullable = true;
            Object minInclusive = null;
            Object minExclusive = null;
            Object maxInclusive = null;
            Object maxExclusive = null;
            String pattern = null;
            List<Object> enumeration = null;
            if (model.get(Constants.MIN_LENGTH) != null)
                minLength = Long.parseLong(model.get(Constants.MIN_LENGTH).toString()) ;
            if (model.get(Constants.MAX_LENGTH) != null)
                maxLength = Long.parseLong(model.get(Constants.MAX_LENGTH).toString()) ;
            if (model.get(Constants.RESOLVER) != null)
                resolver = (ResolverDTO) ResolverDTO.toDTO((Map<String, Object>) model.get(Constants.RESOLVER));
            if (model.get(Constants.NULLABLE) != null)
                nullable = (Boolean) model.get(Constants.NULLABLE);
            if (model.get(Constants.MIN_INCLUSIVE) != null)
                minInclusive = model.get(Constants.MIN_INCLUSIVE);
            if (model.get(Constants.MIN_EXCLUSIVE) != null)
                minExclusive = model.get(Constants.MIN_EXCLUSIVE);
            if (model.get(Constants.MAX_INCLUSIVE) != null)
                maxInclusive = model.get(Constants.MAX_INCLUSIVE);
            if (model.get(Constants.MAX_EXCLUSIVE) != null)
                maxExclusive = model.get(Constants.MAX_EXCLUSIVE);
            if (model.get(Constants.PATTERN) != null)
                pattern = (String) model.get(Constants.PATTERN);
            if (model.get(Constants.ENUMERATION) != null)
                enumeration = (List<Object>) model.get(Constants.ENUMERATION);
            return new ConstraintDTO(minLength, maxLength, resolver, nullable, minInclusive, minExclusive,
                    maxInclusive, maxExclusive, pattern, enumeration);
        }
    }
}
