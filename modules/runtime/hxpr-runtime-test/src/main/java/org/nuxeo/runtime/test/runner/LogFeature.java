/*
 * (C) Copyright 2017-2018 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Thomas Roger
 */
package org.nuxeo.runtime.test.runner;

import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;

/**
 * @since 9.3
 */
public class LogFeature implements RunnerFeature {

    protected static final String CONSOLE_APPENDER = "CONSOLE";

    protected static final String CONSOLE_LOG_FEATURE_APPENDER = "CONSOLE_LOG_FEATURE";

    protected FeaturesLoader loader;

    protected ConsoleAppender<ILoggingEvent> consoleAppender;

    protected ConsoleAppender<ILoggingEvent> hiddenAppender;

    /**
     * Stores the original log level for a given logger name, which allows us to restore the level as defined before
     * launching the tests.
     *
     * @since 11.1
     */
    protected Map<LoggerLevelKey, Level> originalLevelByLogger = new ConcurrentHashMap<>();

    @Override
    public void initialize(FeaturesLoader loader) {
        this.loader = loader;
    }

    /**
     * {@inheritDoc}
     * <p>
     *
     * @since 11.1
     */
    @Override
    public void beforeRun() {
        originalLevelByLogger.clear();
        addOrUpdateLoggerLevel(loader, null);
        addConsoleThresholdLogLevel(loader, null);
    }

    /**
     * {@inheritDoc}
     * <p>
     *
     * @since 11.1
     */
    @Override
    public void afterRun() {
        restoreLoggerLevel(loader, null);
        restoreConsoleThresholdLogLevel(loader, null);
    }

    /**
     * {@inheritDoc}
     * <p>
     *
     * @since 11.1
     */
    @Override
    public void beforeMethodRun(Method method, Object test) {
        addOrUpdateLoggerLevel(loader, method);
        addConsoleThresholdLogLevel(loader, method);
    }

    /**
     * {@inheritDoc}
     * <p>
     *
     * @since 11.1
     */
    @Override
    public void afterMethodRun(Method method, Object test) {
        restoreLoggerLevel(loader, method);
        restoreConsoleThresholdLogLevel(loader, method);
    }

    /**
     * @since 9.10
     */
    public void setConsoleLogThreshold(String level) {
        if (consoleAppender != null) {
            return;
        }

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(ROOT_LOGGER_NAME);
        if (rootLogger == null) {
            System.err.println("No root logger"); // NOSONAR
            return;
        }
        Appender<ILoggingEvent> appender = rootLogger.getAppender(CONSOLE_APPENDER);
        if (appender == null) {
            System.err.println("No root " + CONSOLE_APPENDER + " appender"); // NOSONAR
            return;
        }
        if (!(appender instanceof ConsoleAppender ca)) {
            System.err.println( // NOSONAR
                    CONSOLE_APPENDER + " appender is not a ConsoleAppender but " + appender.getClass().getName());
            return;
        }
        consoleAppender = ca;

        ThresholdFilter filter = new ThresholdFilter();
        filter.setLevel(level);
        filter.start();
        ConsoleAppender<ILoggingEvent> newAppender = new ConsoleAppender<>(); // targets System.out
        newAppender.setContext(context);
        newAppender.setName(CONSOLE_LOG_FEATURE_APPENDER);
        newAppender.setEncoder(ca.getEncoder());
        newAppender.addFilter(filter);
        newAppender.start();

        rootLogger.detachAppender(CONSOLE_APPENDER);
        rootLogger.addAppender(newAppender);
        hiddenAppender = newAppender;
    }

    public void restoreConsoleLog() {
        if (consoleAppender == null) {
            return;
        }

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(ROOT_LOGGER_NAME);
        rootLogger.detachAppender(hiddenAppender);
        rootLogger.addAppender(consoleAppender);
        consoleAppender = null;
        hiddenAppender = null;
    }

    /**
     * Adds the console threshold log level. To be proceed a {@code Class} / {@code Method} should be annotated by
     *
     * @see ConsoleLogLevelThreshold
     *      <p>
     * @see #setConsoleLogThreshold(String)
     * @param runner the feature runner, cannot be {@code null}
     * @param method the framework method, can be {@code null}
     * @since 11.1
     */
    protected void addConsoleThresholdLogLevel(FeaturesLoader loader, Method method) {
        // Remove the previous threshold if any.
        restoreConsoleLog();
        // Set the new threshold
        ConsoleLogLevelThreshold consoleLogThreshold = getAnnotation(loader, method, ConsoleLogLevelThreshold.class);
        if (consoleLogThreshold.value() != null) {
            setConsoleLogThreshold(consoleLogThreshold.value());
        }
    }

    /**
     * Restores the console threshold log level. Based on if {@code Class} or {@code Method} is annotated by
     * {@link ConsoleLogLevelThreshold}.
     * <p>
     * {@link #restoreConsoleLog()}
     *
     * @since 11.1
     */
    protected void restoreConsoleThresholdLogLevel(FeaturesLoader loader, Method method) {
        ConsoleLogLevelThreshold consoleLogThreshold = getAnnotation(loader, method, ConsoleLogLevelThreshold.class);
        if (consoleLogThreshold.value() != null) {
            restoreConsoleLog();
        }
    }

    /**
     * Adds or updates the logger level.
     * <p>
     * The definition of {@link LoggerLevel} can be done on a given {@code Class} / {@code Method} test. At the end of
     * the test each overriding logger must be restored to its original value for this the purpose we should save the
     * original level.
     * <p>
     * {@link #restoreLoggerLevel(FeaturesRunner, Method)} to see how the restore part will be happened.
     *
     * @param runner the feature runner, cannot be {@code null}
     * @param method the framework method, can be {@code null}
     * @since 11.1
     */
    protected void addOrUpdateLoggerLevel(FeaturesLoader loader, Method method) {
        for (LoggerLevel loggerLevel : getLoggers(loader, method)) {
            if (loggerLevel.level() != null) {
                String loggerName = getLoggerName(loggerLevel);
                LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
                // Initialize the undefined logger to simplify the restoring step. There is no way to remove / delete
                // a logger this is why we turned it to OFF.
                Logger logger = context.exists(loggerName);
                if (logger == null) {
                    logger = context.getLogger(loggerName);
                    logger.setLevel(Level.OFF);
                }
                // Save the original value.
                originalLevelByLogger.put(buildKey(loggerLevel, method), logger.getEffectiveLevel());
                // Set the new level that we want
                logger.setLevel(Level.toLevel(loggerLevel.level()));
            }
        }
    }

    /**
     * Restores the original value of the logger level.
     * <p>
     * {@link #addOrUpdateLoggerLevel(FeaturesRunner, Method)}} to see how we store the original value and set
     * the new one.
     *
     * @param runner the feature runner, cannot be {@code null}
     * @param method the framework method, can be {@code null}
     * @since 11.1
     */
    protected void restoreLoggerLevel(FeaturesLoader loader, Method method) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (LoggerLevel loggerLevel : getLoggers(loader, method)) {
            if (loggerLevel.level() != null) {
                String loggerName = getLoggerName(loggerLevel);
                Logger logger = context.getLogger(loggerName);
                Level level = originalLevelByLogger.remove(buildKey(loggerLevel, method));
                logger.setLevel(level);
            }
        }
    }

    /**
     * Retrieves the {@link LoggerLevel}, a {@code Class} or a {@code Method} can be annotated by one or more Logger.
     *
     * @since 11.1
     */
    protected List<LoggerLevel> getLoggers(FeaturesLoader loader, Method method) {
        // Unique annotation LoggerLevel.
        List<LoggerLevel> loggers = new ArrayList<>(List.of(getAnnotation(loader, method, LoggerLevel.class)));

        // Repeatable annotation LoggerLevel using LoggerLevels.
        LoggerLevels configs = getAnnotation(loader, method, LoggerLevels.class);
        if (configs.value() != null) {
            loggers.addAll(List.of(configs.value()));
        }

        return loggers;
    }

    /**
     * Gets the annotation for a given {@code FeaturesRunner}, {@code Method} and a {@code Class} type.
     *
     * @since 11.1
     */
    protected <T extends Annotation> T getAnnotation(FeaturesLoader loader, Method method, Class<T> type) {
        return method != null ? loader.getConfig(method, type) : loader.getConfig(type);

    }

    /**
     * Gets the logger name from a given {@link LoggerLevel}.
     *
     * @since 11.1
     */
    protected String getLoggerName(LoggerLevel logLevel) {
        return StringUtils.defaultIfBlank(logLevel.name(), logLevel.klass().getName());
    }

    /**
     * Builds the logger key.
     *
     * @since 11.1
     */
    protected LoggerLevelKey buildKey(LoggerLevel logger, Method method) {
        ElementType type = method != null ? ElementType.METHOD : ElementType.TYPE;
        String loggerName = getLoggerName(logger);
        return new LoggerLevelKey(type, loggerName);
    }
}
