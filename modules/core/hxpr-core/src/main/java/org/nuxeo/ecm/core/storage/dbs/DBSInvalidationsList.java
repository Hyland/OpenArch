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
package org.nuxeo.ecm.core.storage.dbs;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import com.hyland.openarch.runtime.pubsub.SerializableAccumulableInvalidations;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class DBSInvalidationsList implements SerializableAccumulableInvalidations {

    private static final ObjectMapper MAPPER = new ObjectMapper().rebuild()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected List<DBSInvalidations> invalidations;

    public static DBSInvalidationsList deserialize(String message) {
        DBSInvalidationsList list;
        try {
            list = MAPPER.readValue(message, DBSInvalidationsList.class);
        } catch (MismatchedInputException _) {
            // The json message could be the serialization of a DBSInvalidations
            try {
                var invalidations = MAPPER.readValue(message, DBSInvalidations.class);
                list = new DBSInvalidationsList();
                list.add(invalidations);
            } catch (MismatchedInputException _) {
                return null;
            }
        }

        return list;
    }

    @Override
    public boolean isEmpty() {
        return invalidations == null || invalidations.isEmpty();
    }

    @Override
    public void add(SerializableAccumulableInvalidations o) {
        if (o instanceof DBSInvalidations inval) {
            addOne(inval);
        } else {
            DBSInvalidationsList other = (DBSInvalidationsList) o;
            if (other == null) {
                return;
            }
            if (other.invalidations != null) {
                other.invalidations.forEach(this::addOne);
            }
        }
    }

    private void addOne(DBSInvalidations inval) {
        if (invalidations == null) {
            invalidations = new ArrayList<>();
        }
        // if a previous invalidation has same node and timestamp, merge with it
        DBSInvalidations mergeable = null;
        for (DBSInvalidations other : invalidations) {
            if (other.isMergeableWith(inval)) {
                mergeable = other;
                break;
            }
        }
        if (mergeable == null) {
            invalidations.add(inval);
        } else {
            mergeable.add(inval);
        }
    }

    @Override
    public DBSInvalidationsList minimize() {
        if (invalidations == null) {
            return this;
        } else {
            DBSInvalidationsList other = new DBSInvalidationsList();
            other.invalidations = new ArrayList<>(); // mutable
            invalidations.forEach(inval -> other.invalidations.add(inval.minimize()));
            return other;
        }
    }

    @Override
    public void serialize(Writer writer) throws IOException {
        MAPPER.writeValue(writer, this);
    }

    public List<DBSInvalidations> getInvalidations() {
        return invalidations;
    }

    public void clear() {
        if (invalidations != null) {
            invalidations.clear();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getSimpleName() + '(');
        if (invalidations != null) {
            sb.append("invalidations=[");
            var listInvals = invalidations.stream().map(DBSInvalidations::toString).toList();
            sb.append(String.join(", ", listInvals));
            sb.append(']');
        }
        return sb.append(')').toString();
    }
}
