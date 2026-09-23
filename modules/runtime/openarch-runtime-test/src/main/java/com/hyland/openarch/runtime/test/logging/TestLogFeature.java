/*
 * (C) Copyright 2019-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.runtime.test.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.runtime.test.runner.ConsoleLogLevelThreshold;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.LogFeature;
import com.hyland.openarch.runtime.test.runner.LoggerLevel;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Test for {@link LogFeature}.
 *
 * @since 11.1
 */
@ExtendWith(FeaturesExtension.class)
@Features({ RuntimeFeature.class, LogFeature.class })
@LoggerLevel(name = "org.nuxeo.FakeClass2", level = "DEBUG")
@LoggerLevel(name = "org.nuxeo.FakeClass3", level = "ERROR")
public class TestLogFeature {

    protected static final Logger log = LoggerFactory.getLogger(TestLogFeature.class);

    protected static final String FAKE_LOGGER_NAME_1 = "org.nuxeo.FakeClass1";

    protected static final String FAKE_LOGGER_NAME_2 = "org.nuxeo.FakeClass2";

    protected static final String FAKE_LOGGER_NAME_3 = "org.nuxeo.FakeClass3";

    @Test
    @LoggerLevel(name = FAKE_LOGGER_NAME_1, level = "TRACE")
    public void shouldAddNewLogger() {
        assertTrue(LoggerFactory.getLogger(FAKE_LOGGER_NAME_1).isTraceEnabled());
    }

    @Test
    @LoggerLevel(klass = TestLogFeature.class, level = "DEBUG")
    public void shouldUpdateLogger() {
        assertTrue(LoggerFactory.getLogger(TestLogFeature.class).isDebugEnabled());
    }

    @Test
    @LoggerLevel(name = FAKE_LOGGER_NAME_2, level = "TRACE")
    @LoggerLevel(name = FAKE_LOGGER_NAME_3, level = "INFO")
    public void shouldOverrideTestLoggerDefinition() {
        assertTrue(LoggerFactory.getLogger(FAKE_LOGGER_NAME_2).isTraceEnabled());
        assertTrue(LoggerFactory.getLogger(FAKE_LOGGER_NAME_3).isInfoEnabled());
    }

    @Test
    public void shouldInheritLogger() {
        assertTrue(LoggerFactory.getLogger(FAKE_LOGGER_NAME_2).isDebugEnabled());
    }

    @Test
    @ConsoleLogLevelThreshold("ERROR")
    public void shouldSetConsoleLogThreshold() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = context.getLogger(ROOT_LOGGER_NAME);
        Appender<ILoggingEvent> appender = rootLogger.getAppender("CONSOLE_LOG_FEATURE");
        assertNotNull(appender);
        List<Filter<ILoggingEvent>> filters = appender.getCopyOfAttachedFiltersList();
        assertNotNull(filters);
        assertEquals(1, filters.size());
        Filter<ILoggingEvent> filter = filters.get(0);
        assertNotNull(filter);
        assertTrue(filter instanceof ThresholdFilter);
        ThresholdFilter threshold = (ThresholdFilter) filter;
        // can't get level directly as it's package-private; test using decide
        LoggingEvent error = new LoggingEvent();
        error.setLevel(Level.ERROR);
        assertEquals(FilterReply.NEUTRAL, threshold.decide(error));
        LoggingEvent debug = new LoggingEvent();
        debug.setLevel(Level.DEBUG);
        assertEquals(FilterReply.DENY, threshold.decide(debug));
    }

}
