/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.exception;

import java.util.Arrays;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record HxprErrorResponse(@JsonProperty(value = HxprErrorResponse.ENTITY_TYPE_PROP_NAME, index = 0) String entityType, int status,
                                String message,
                                @JsonInclude(JsonInclude.Include.NON_NULL) StackTraceElement[] stackTrace,
                                @JsonInclude(JsonInclude.Include.NON_NULL) Throwable exception) {

    public static final String ENTITY_TYPE_PROP_NAME = "entity-type";
    public static final String STATUS_PROP_NAME = "status";

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        HxprErrorResponse that = (HxprErrorResponse) obj;
        if (status != that.status) {
            return false;
        }
        if (!Objects.equals(entityType, that.entityType)) {
            return false;
        }
        if (!Objects.equals(message, that.message)) {
            return false;
        }
        if (!Arrays.equals(stackTrace, that.stackTrace)) {
            return false;
        }
        return Objects.equals(exception, that.exception);
    }

    @Override
    public int hashCode() {
        int result = entityType != null ? entityType.hashCode() : 0;
        result = 31 * result + status;
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(stackTrace);
        result = 31 * result + (exception != null ? exception.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "HxprErrorResponse{" + //
                "entityType='" + entityType + '\'' + //
                ", status=" + status + //
                ", message='" + message + '\'' + //
                ", stackTrace=" + Arrays.toString(stackTrace) + //
                ", exception=" + exception + //
                '}';
    }
}
