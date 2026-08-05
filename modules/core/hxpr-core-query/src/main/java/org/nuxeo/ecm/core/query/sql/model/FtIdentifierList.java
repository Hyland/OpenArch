/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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
import java.util.Arrays;
import java.util.List;

public class FtIdentifierList extends ArrayList<String> implements Operand {

    private static final long serialVersionUID = 1L;

    public FtIdentifierList() {
        super();
    }

    public FtIdentifierList(List<String> list) {
        super(list);
    }

    public FtIdentifierList(String identifiers) {
        this.addAll(Arrays.asList(identifiers.split(",")));
    }

    @Override
    public void accept(IVisitor visitor) {
        // nothing
    }

    @Override
    public String toString() {
        return String.join(",", this);
    }

}
