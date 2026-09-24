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
package com.hyland.openarch.ecm.core.event.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import com.hyland.openarch.async.tasks.AsyncTasksFeature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.event.EventService;
import com.hyland.openarch.runtime.RuntimeMessage.Level;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.cluster.BasicClusterFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features({ BasicClusterFeature.class, AsyncTasksFeature.class })
@Deploy("com.hyland.openarch.ecm.core.event")
public class TestEventListenerContrib {

    @Test
    @Deploy("com.hyland.openarch.ecm.core.event.test:test-InvalidListeners.xml")
    public void testInvalidListeners() throws Exception {
        List<String> errors = Framework.getRuntime().getMessageHandler().getMessages(Level.ERROR);
        assertNotNull(errors);
        assertEquals(3, errors.size());

        assertEquals("Failed to register event listener in component 'test-invalid-listeners#listener': "
                + "error initializing event listener 'invalidListenerUnknown' (com.hyland.openarch.ecm.core.api.RepositoryException: "
                + "java.lang.ClassNotFoundException: org.nuxeo.invalid.listener.UnknownClass)", errors.get(0));
        assertEquals("Failed to register event listener in component 'test-invalid-listeners#listener': "
                + "error initializing event listener 'invalidListenerNotEventListener' "
                + "(java.lang.IllegalArgumentException: Listener extension must define a class extending "
                + "EventListener or PostCommitEventListener: 'com.hyland.openarch.ecm.core.event.test.InvalidEventListener'.)",
                errors.get(1));
        assertEquals("Failed to register event listener in component 'test-invalid-listeners#listener': "
                + "error initializing event listener 'invalidListenerNoRef' (java.lang.IllegalArgumentException: "
                + "Listener extension must define either a class or a script)", errors.get(2));
    }

    protected EventService getService() {
        return Framework.getService(EventService.class);
    }

}
