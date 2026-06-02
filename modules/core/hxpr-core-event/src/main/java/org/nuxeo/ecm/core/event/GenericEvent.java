/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/) and others.
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
package org.nuxeo.ecm.core.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class GenericEvent {

    public static final String GENERIC_TYPE_KEY_PATTERN = "%s-%s";
    public static final String EVENT_NAME_PREFIX = "generic";
    public static final String DATA = "data";

    private static final ObjectMapper MAPPER;
    static {
        // Configure ObjectMapper to serialize all fields (including private ones) and exclude null values.
        // This ensures that the event payloads are complete and concise for downstream consumers.
        // - This static configuration guarantees consistent serialization for all subclasses of GenericEvent.
        MAPPER = new ObjectMapper();
        MAPPER.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        MAPPER.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    protected GenericEvent() {
    }

    @JsonIgnore
    public abstract String getGenericType();

    protected String asString() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new NuxeoException(e);
        }
    }

    protected String getEventName() {
        return GENERIC_TYPE_KEY_PATTERN.formatted(EVENT_NAME_PREFIX, getGenericType());
    }

    public void fire() {
        EventContext ctx = new EventContextImpl();
        ctx.setProperty(DATA, asString());
        Event event = ctx.newEvent(getEventName());
        Framework.getService(EventService.class).fireEvent(event);
    }
}
