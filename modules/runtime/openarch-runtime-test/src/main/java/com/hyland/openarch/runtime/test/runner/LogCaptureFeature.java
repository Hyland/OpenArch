/*
 * (C) Copyright 2012-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.test.runner;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.runtime.RuntimeServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;

/**
 * Test feature to capture from a log4j appender to check that some log4j calls have been correctly called.
 * <p>
 * On a test class or a test method using this feature, a default filter can be configured with the annotation
 * {@link LogCaptureFeature.FilterOn} or a custom one implementing {@link LogCaptureFeature.Filter} class can be
 * provided with the annotation {@link LogCaptureFeature.FilterWith} to select the log events to capture.
 * <p>
 * A {@link LogCaptureFeature.Result} instance is to be injected with {@link jakarta.inject.Inject} as an attribute of the
 * test.
 * <p>
 * The method {@link LogCaptureFeature.Result#assertHasEvent()} can then be called from test methods to check that
 * matching log calls (events) have been captured.
 *
 * @since 5.7
 */
public class LogCaptureFeature implements RunnerFeature {

    private static final Logger log = LoggerFactory.getLogger(LogCaptureFeature.class);

    /**
     * @since 5.7
     * @since since 10.3, this exception is an {@link AssertionError}
     */
    public static class NoLogCaptureFilterException extends AssertionError {
        private static final long serialVersionUID = 1L;
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    public @interface FilterWith {
        /**
         * Custom implementation of a filter to select event to capture.
         */
        Class<? extends LogCaptureFeature.Filter> value();
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    public @interface FilterOn {
        /**
         * Configuration for the default filter
         */
        String loggerName() default "";

        String logLevel() default "";

        Class<?> loggerClass() default Object.class;
    }

    protected final class CapturingAppender extends AppenderBase<ILoggingEvent> {
        @Override
        public void append(ILoggingEvent event) {
            if (currentFilter == null) {
                myResult.noFilterFlag = true;
            } else if (currentFilter.accept(event)) {
                myResult.addMessage(event.getMessage());
            }
        }
    }

    /**
     * Default OpenArch filter which takes a logger name and a log level to accept only events matching both.
     * <p>
     * Null or empty criteria are converted to match all of them.
     * <p>
     * For instance, filter will match all loggers if given logger name is null or empty.
     *
     * @since 8.10
     */
    protected static class DefaultFilter implements LogCaptureFeature.Filter {

        protected final String loggerName;

        protected final Level logLevel;

        public DefaultFilter(String loggerName, String logLevel) {
            super();
            this.loggerName = StringUtils.stripToNull(loggerName);
            this.logLevel = StringUtils.isBlank(logLevel) ? null : Level.toLevel(logLevel);
        }

        @Override
        public boolean accept(ILoggingEvent event) {
            if (logLevel != null && !logLevel.equals(event.getLevel())) {
                return false;
            }
            return loggerName == null || loggerName.equals(event.getLoggerName());
        }
    }

    /**
     * Log result class.
     */
    public static class Result {
        protected final List<String> messages = new ArrayList<>();

        protected boolean noFilterFlag = false;

        public void assertHasEvent() {
            if (noFilterFlag) {
                throw new LogCaptureFeature.NoLogCaptureFilterException();
            }
            assertFalse(messages.isEmpty(), "No log result found");
        }

        public void clear() {
            messages.clear();
            noFilterFlag = false;
        }

        public List<String> getCaughtEventMessages() {
            return messages;
        }

        public void addMessage(String message) {
            messages.add(message);
        }

    }

    @FunctionalInterface
    public interface Filter {
        /**
         * {@link LogCaptureFeature} will capture the event if it does match the implementation condition.
         */
        boolean accept(ILoggingEvent event);
    }

    protected FeaturesLoader loader;

    /** Filter defined on class. */
    protected Filter classFilter;

    /** Filter defined on method. */
    protected Filter methodFilter;

    /** Filter used when appending a log to {@link #logAppender appender}. */
    protected volatile Filter currentFilter; // NOSONAR test code

    protected final Result myResult = new Result();

    /**
     * An {@link Appender} added to the context at beginning of tests.
     */
    protected Appender<ILoggingEvent> logAppender;

    @Override
    public void initialize(FeaturesLoader loader) {
        this.loader = loader;
    }

    @Override
    public void beforeRun() {
        // create class filter
        classFilter = instantiateFilter(() -> loader.getConfig(FilterWith.class),
                () -> loader.getConfig(FilterOn.class));
        if (classFilter == null) {
            log.info("Class {} uses LogCaptureFeature without defining a filter",
                    loader.getTargetTestClass().getName());
        }
        // set current filter and start appender
        currentFilter = classFilter;

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        logAppender = new CapturingAppender();
        logAppender.setContext(context);
        logAppender.setName("LOG_CAPTURE_APPENDER");
        logAppender.start();
        context.getLogger(ROOT_LOGGER_NAME).addAppender(logAppender);
    }

    @Override
    public void configure(com.google.inject.Binder binder) {
        binder.bind(Result.class).toInstance(myResult);
    }

    @Override
    public void beforeMethodRun(Method method, Object test) {
        // re-init result
        myResult.clear();
        // create method filter
        methodFilter = instantiateFilter(() -> loader.getConfig(method, FilterWith.class),
                () -> loader.getConfig(method, FilterOn.class));
        if (methodFilter == null) {
            log.info("Method {} uses LogCaptureFeature without defining a filter", method.getName());
        }
        // set current filter
        currentFilter = methodFilter;
    }

    @Override
    public void afterMethodRun(Method method, Object test) {
        // re-init result
        myResult.clear();
        // discard method filter
        methodFilter = null;
        // set current filter
        currentFilter = classFilter;
    }

    @Override
    public void afterRun() {
        // discard class filter
        classFilter = null;
        currentFilter = null;
        // don't stop appender as it could still be called after removed (race condition)
        // nevertheless this doesn't affect us as we're outside tests
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(ROOT_LOGGER_NAME).detachAppender(logAppender);
    }

    protected Filter instantiateFilter(Supplier<FilterWith> filterWith, Supplier<FilterOn> filterOn) {
        // create filter
        Filter filter;
        FilterWith filterProvider = filterWith.get();
        // value can be null even if JDK doesn't allow it
        // this is due to our proxy which return the default value of annotation and in our case it doesn't exist
        // noinspection ConstantConditions
        if (filterProvider.value() == null) {
            FilterOn defaultFilterConfig = filterOn.get();
            // check if there's at least one attribute defined
            if (isNotBlank(defaultFilterConfig.loggerName()) || isNotBlank(defaultFilterConfig.logLevel())
                    || defaultFilterConfig.loggerClass() != Object.class) {
                // switch between loggerClass or loggerName
                if (defaultFilterConfig.loggerClass() != Object.class) {
                    String loggerName = defaultFilterConfig.loggerClass().getName();
                    filter = new DefaultFilter(loggerName, defaultFilterConfig.logLevel());
                } else {
                    filter = new DefaultFilter(defaultFilterConfig.loggerName(), defaultFilterConfig.logLevel());
                }
            } else {
                return null;
            }
        } else {
            try {
                filter = filterProvider.value().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeServiceException(e);
            }
        }
        return filter;
    }
}
