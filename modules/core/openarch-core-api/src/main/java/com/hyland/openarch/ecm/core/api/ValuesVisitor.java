/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.api;

import java.util.Comparator;
import java.util.List;

import com.hyland.openarch.ecm.core.schema.types.Field;

/**
 * A visitor for a document's values.
 * <p>
 * A visitor has to provide {@link #keepBlobs} information, and if relevant a {@link #getXPath}, and the other methods
 * will be called to reflect the structure of the document being visited.
 */
public interface ValuesVisitor {

    /** Whether this visitor is interested in real blobs (as opposed to keeping them as sub-objects). */
    boolean visitActualBlobs();

    /** The key sorter, or {@code null} for no sort. */
    default Comparator<String> keySorter() {
        return null;
    }

    /** Gets the current xpath for the object being visited. */
    String getXPath();

    void startObject();

    void endObject();

    void startObjectElement(Field field);

    void endObjectElement();

    void startList();

    void endList();

    void startListElement(int i);

    void endListElement();

    /**
     * Called to visit a scalar value.
     *
     * @param value the value
     * @param field the field for the value
     */
    void value(Object value, Field field);

    /**
     * Called to visit a list of values.
     *
     * @param list the list
     * @param listField the field for the list
     * @param elemField the field for a list element
     */
    void list(List<Object> list, Field listField, Field elemField);

    /**
     * Called to visit a blob, if {@link #visitActualBlobs} is {@code true}.
     * <p>
     * {@code state} is provided for the specific use case of rewriting blobs.
     */
    void blob(Blob blob, Object state);

}
