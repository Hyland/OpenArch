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
package com.hyland.openarch.ecm.core.schema.types.primitives;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.schema.types.PrimitiveType;
import com.hyland.openarch.ecm.core.schema.types.constraints.Constraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.NotNullConstraint;

/**
 * The binary type handles values of type InputStream.
 */
public final class BinaryType extends PrimitiveType {

    private static final Logger log = LogManager.getLogger(BinaryType.class);

    public static final String ID = "binary";

    public static final BinaryType INSTANCE = new BinaryType();

    private BinaryType() {
        super(ID);
    }

    @Override
    public Object convert(Object value) {
        if (value instanceof CharSequence) {
            return new ByteArrayInputStream(value.toString().getBytes());
        } else if (value instanceof byte[] bytes) {
            return new ByteArrayInputStream(bytes);
        } else if (value instanceof InputStream) {
            return value;
        }
        return null;
    }

    public static Object parseString(String str) {
        return new ByteArrayInputStream(str.getBytes());
    }

    protected Object readResolve() {
        return INSTANCE;
    }

    @Override
    public Object decode(String str) {
        if (StringUtils.isEmpty(str)) {
            return null;
        }
        return new ByteArrayInputStream(str.getBytes());
    }

    @Override
    public String encode(Object object) {
        if (object instanceof InputStream in) {
            try (in) {
                return IOUtils.toString(in, UTF_8);
            } catch (IOException e) {
                log.error(e, e);
                return null;
            }
        }
        return object.toString();
    }

    @Override
    public boolean support(Class<? extends Constraint> constraint) {
        return NotNullConstraint.class.equals(constraint);
    }

}
