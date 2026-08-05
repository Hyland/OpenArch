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

import org.apache.commons.lang3.StringUtils;

/**
 * A named reference to a variable (this can be a field or table).
 * <p>
 * Can also include a cast.
 */
public class Reference implements Operand {

    public final String name;

    private String originalName;

    public final String cast;

    public final FtHint ftHint;

    /** Arbitrary info associated to the reference. */
    private Object info;

    public Reference(String name) {
        this.name = name;
        cast = null;
        ftHint = null;
    }

    /** @since 5.6 */
    public Reference(String name, String cast) {
        this.name = name;
        this.cast = cast;
        ftHint = null;
    }

    /** @since 9.10 */
    public Reference(String name, String cast, FtHint hint) {
        this.name = name;
        this.cast = cast;
        this.ftHint = hint;
    }

    /** @since 5.6 */
    public Reference(Reference other, String cast) {
        this.name = other.name;
        this.cast = cast;
        ftHint = null;
    }

    /** @since 7.3 */
    public Reference(Reference other, FtHint hint) {
        this.name = other.name;
        cast = null;
        this.ftHint = hint;
    }

    @Override
    public void accept(IVisitor visitor) {
        visitor.visitReference(this);
    }

    public void setInfo(Object info) {
        this.info = info;
    }

    public Object getInfo() {
        return info;
    }

    @Override
    public String toString() {
        if (cast != null) {
            return cast + '(' + name + ')';
        } else if (ftHint != null) {
            return ftHint.toString() + " " + name;
        }
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Reference)) {
            return false;
        }
        return equals((Reference) obj);
    }

    private boolean equals(Reference other) {
        if (!name.equals(other.name)) {
            return false;
        }
        if (cast != null || other.cast != null) {
            return StringUtils.equals(cast, other.cast);
        }
        if (ftHint != null) {
            return ftHint.equals(other.ftHint);
        } else if (other.ftHint != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = 31 + (cast == null ? 0 : cast.hashCode()) + (ftHint == null ? 0 : ftHint.hashCode());
        return 31 * result + name.hashCode();
    }

    public boolean isPathReference() {
        return false;
    }

    public String getOriginalName() {
        return this.originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

}
