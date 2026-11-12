/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.springboot;

import static com.hyland.openarch.common.Environment.RUNTIME_HOME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hyland.openarch.common.Environment;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.springboot.application.loader.SpringBootFrameworkLoader;
import com.hyland.openarch.runtime.springboot.framework.ApplicationContextHelper;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class OpenArchApplicationContextTest {

    @TempDir
    Path runtimeHome;

    @Test
    void exposesSpringContextBeforeStartingOpenArchComponents() {
        ApplicationContext context = mock(ApplicationContext.class);
        MockEnvironment environment = new MockEnvironment().withProperty(RUNTIME_HOME, runtimeHome.toString());
        when(context.getEnvironment()).thenReturn(environment);
        OpenArchApplication application = spy(new OpenArchApplication());
        doReturn(Map.of()).when(application).getBundles();
        doReturn(Map.of()).when(application).buildRepositoryConfEnv(environment);

        ApplicationContext previousContext = ApplicationContextHelper.getApplicationContext();
        String previousHome = System.getProperty(Environment.HOME);
        ApplicationContextHelper.setApplicationContext(null);
        try (var loader = mockStatic(SpringBootFrameworkLoader.class);
             var framework = mockStatic(Framework.class)) {
            framework.when(Framework::isInitialized).thenReturn(false);
            loader.when(() -> SpringBootFrameworkLoader.start(any(ClassLoader.class), any(File.class),
                    anyList(), anyMap())).thenAnswer(ignored -> {
                assertSame(context, ApplicationContextHelper.getApplicationContext());
                return null;
            });

            application.start(context);

            loader.verify(() -> SpringBootFrameworkLoader.start(any(ClassLoader.class), any(File.class),
                    anyList(), anyMap()));
            assertEquals(OpenArchApplication.STATUS_STARTED, application.getStatus());
        } finally {
            ApplicationContextHelper.setApplicationContext(previousContext);
            if (previousHome == null) {
                System.clearProperty(Environment.HOME);
            } else {
                System.setProperty(Environment.HOME, previousHome);
            }
        }
    }
}
