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
package org.nuxeo.ecm.core.query.sql.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SelectList extends ArrayList<Operand> implements ASTNode {

    private static final long serialVersionUID = 1L;

    public SelectList() {
        super();
    }

    public SelectList(List<Operand> list) {
        super(list);
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "*";
        }
        return stream().map(Operand::toString).collect(Collectors.joining(", "));
    }

    @Override
    public void accept(IVisitor visitor) {
        visitor.visitSelectList(this);
    }

}
