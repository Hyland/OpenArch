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

package com.hyland.openarch.runtime.model.impl;

import java.net.URL;

import com.hyland.openarch.common.xmap.Context;
import com.hyland.openarch.runtime.model.RuntimeContext;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class XMapContext extends Context {

    private static final long serialVersionUID = 1L;

    private final RuntimeContext ctx;

    public XMapContext(RuntimeContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Returns the underlying runtime context.
     *
     * @since 11.5
     */
    public RuntimeContext getRuntimeContext() {
        return ctx;
    }

    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        if (className.startsWith("[")) {
            return Class.forName(className, true, Thread.currentThread().getContextClassLoader());
        }
        return ctx.loadClass(className);
    }

    @Override
    public URL getResource(String name) {
        URL url = ctx.getLocalResource(name);
        if (url == null) {
            url = ctx.getResource(name);
        }
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof XMapContext xmc)) {
            return false;
        }
        return ctx.equals(xmc.ctx);
    }

    @Override
    public int hashCode() {
        return ctx.hashCode();
    }
}
