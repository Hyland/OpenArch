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
package org.nuxeo.ecm.core.api;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.ecm.core.schema.types.Field;

/**
 * Base implementation for a {@link ValuesVisitor} maintaining the xpath.
 */
public abstract class BaseValuesVisitor implements ValuesVisitor {

    // list of Field or Integer
    // we use this instead of just a list of Strings because of ConstraintViolation needs
    protected final List<Object> xpath = new ArrayList<>();

    @Override
    public String getXPath() {
        List<String> list = xpath.stream().map(o -> o instanceof Field f ? f.getName() : o.toString()).toList();
        return StringUtils.join(list, '/');
    }

    @Override
    public void startObject() {
        // nothing
    }

    @Override
    public void endObject() {
        // nothing
    }

    @Override
    public void startObjectElement(Field field) {
        xpath.add(field);
    }

    @Override
    public void endObjectElement() {
        xpath.remove(xpath.size() - 1);
    }

    @Override
    public void startList() {
        // nothing
    }

    @Override
    public void endList() {
        // nothing
    }

    @Override
    public void startListElement(int i) {
        xpath.add(Integer.valueOf(i));
    }

    @Override
    public void endListElement() {
        xpath.remove(xpath.size() - 1);
    }

    @Override
    public void value(Object value, Field field) {
        // nothing
    }

    @Override
    public void list(List<Object> list, Field listField, Field elemField) {
        // nothing
    }

    @Override
    public void blob(Blob blob, Object state) {
        // nothing
    }

}
