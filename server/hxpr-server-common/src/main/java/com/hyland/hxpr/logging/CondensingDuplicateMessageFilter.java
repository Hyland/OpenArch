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
package com.hyland.hxpr.logging;

import static org.nuxeo.rest.interceptor.APILatencyLoggingInterceptor.BYPASS_CONDENSING_MARKER_NAME;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A Logback {@link TurboFilter} that suppresses repeated log messages within a rolling time window.
 * <p>
 * Within each {@code summaryIntervalSeconds} window, the first {@code allowedRepetitions} occurrences of a message are
 * allowed through. Further duplicates within the same window are suppressed. At the end of the window, a summary line
 * is emitted for any suppressed messages and all counters are reset -- so the next window starts fresh.
 * <p>
 * Messages are keyed on the format string as received by the TurboFilter. When callers use SLF4J directly with
 * parameterised logging (e.g. {@code log.info("Error {} with node id {}", code, id)}), the format retains its
 * {@code {}} placeholders and all invocations with different parameter values group under one key.
 * <p>
 * <strong>Limitation:</strong> Code that uses the Log4j2 API ({@code org.apache.logging.log4j.Logger}) is routed
 * through the {@code log4j-to-slf4j} bridge, which resolves parameters <em>before</em> the message reaches this filter.
 * In that case each unique resolved string is treated as a separate key. The filter still suppresses exact duplicate
 * messages (which covers the primary use case of repeated identical error messages during an incident), but will not
 * group messages that differ only in their parameter values.
 */
public class CondensingDuplicateMessageFilter extends TurboFilter {

    private static final String SELF_LOGGER_NAME = CondensingDuplicateMessageFilter.class.getName();

    private volatile int allowedRepetitions = 10;

    private volatile int summaryIntervalSeconds = 60;

    private volatile int cacheSize = 100;

    private ConcurrentHashMap<String, MessageStats> messageCounts;

    private ScheduledExecutorService scheduler;

    @Override
    public void start() {
        messageCounts = new ConcurrentHashMap<>();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "condensing-log-filter");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::emitSummaries, summaryIntervalSeconds, summaryIntervalSeconds,
                TimeUnit.SECONDS);
        super.start();
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        emitSummaries();
        super.stop();
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format,
            Object[] params, Throwable t) {
        if (format == null || logger == null || level == null) {
            return FilterReply.NEUTRAL;
        }

        // Never filter our own summary messages
        if (SELF_LOGGER_NAME.equals(logger.getName())) {
            return FilterReply.NEUTRAL;
        }

        // Skip messages that specifically ask not to be filtered.
        if (marker != null && marker.contains(BYPASS_CONDENSING_MARKER_NAME)) {
            return FilterReply.NEUTRAL;
        }

        // Don't count messages that wouldn't pass the logger's level check anyway
        if (!level.isGreaterOrEqual(logger.getEffectiveLevel())) {
            return FilterReply.NEUTRAL;
        }

        if (!messageCounts.containsKey(format)) {
            evictOldest();
        }
        MessageStats stats = messageCounts.computeIfAbsent(format,
                _ -> new MessageStats(new AtomicInteger(0), level, logger.getName(), System.nanoTime()));
        int count = stats.count().incrementAndGet();
        return count <= allowedRepetitions ? FilterReply.NEUTRAL : FilterReply.DENY;
    }

    private void evictOldest() {
        while (messageCounts.size() >= cacheSize) {
            messageCounts.entrySet()
                         .stream()
                         .min(Map.Entry.comparingByValue(Comparator.comparingLong(MessageStats::createdAt)))
                         .ifPresent(oldest -> {
                             messageCounts.remove(oldest.getKey());
                             emitSummaryIfSuppressed(oldest.getKey(), oldest.getValue());
                         });
        }
    }

    private void emitSummaries() {
        messageCounts.forEach(this::emitSummaryIfSuppressed);
        messageCounts.clear();
    }

    private void emitSummaryIfSuppressed(String format, MessageStats stats) {
        int suppressed = stats.count().get() - allowedRepetitions;
        if (suppressed > 0) {
            Logger summaryLogger = (Logger) LoggerFactory.getLogger(stats.loggerName());
            summaryLogger.log(null, SELF_LOGGER_NAME, Level.toLocationAwareLoggerInteger(stats.level()),
                    "Suppressed {} repetitions of message: {}", new Object[] { suppressed, format }, null);
        }
    }

    public int getAllowedRepetitions() {
        return allowedRepetitions;
    }

    public void setAllowedRepetitions(int allowedRepetitions) {
        this.allowedRepetitions = allowedRepetitions;
    }

    public int getSummaryIntervalSeconds() {
        return summaryIntervalSeconds;
    }

    public void setSummaryIntervalSeconds(int summaryIntervalSeconds) {
        this.summaryIntervalSeconds = summaryIntervalSeconds;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }
}
