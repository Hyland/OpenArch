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
package com.hyland.openarch.logging;

import static com.hyland.openarch.logging.LogMarkers.BYPASS_CONDENSING_MARKER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;

class CondensingDuplicateMessageFilterTest {

    private static final String TEST_LOGGER_NAME = "com.hyland.openarch.TestLogger";

    private static final int ALLOWED_REPETITIONS = 3;

    private static final int CACHE_SIZE = 5;

    private CondensingDuplicateMessageFilter filter;

    private ListAppender<ILoggingEvent> listAppender;

    private Logger testLogger;

    @BeforeEach
    void setUp() {
        filter = new CondensingDuplicateMessageFilter();
        filter.setAllowedRepetitions(ALLOWED_REPETITIONS);
        filter.setCacheSize(CACHE_SIZE);
        filter.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        filter.start();

        testLogger = (Logger) LoggerFactory.getLogger(TEST_LOGGER_NAME);
        testLogger.setLevel(Level.ERROR);
        listAppender = new ListAppender<>();
        listAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        listAppender.start();
        testLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        testLogger.detachAppender(listAppender);
        listAppender.stop();
        filter.stop();
    }

    private FilterReply decide(String format) {
        return filter.decide(null, testLogger, Level.ERROR, format, null, null);
    }

    @Test
    void messagesWithinThresholdAreAllowed() {
        IntStream.range(0, ALLOWED_REPETITIONS).forEach(i -> {
            FilterReply reply = decide("Error {} with node id {}");
            assertEquals(FilterReply.NEUTRAL, reply, "Message #" + (i + 1) + " should be allowed");
        });
    }

    @Test
    void messagesExceedingThresholdAreDenied() {
        IntStream.range(0, ALLOWED_REPETITIONS).forEach(_ -> decide("Error {} with node id {}"));

        FilterReply reply = decide("Error {} with node id {}");
        assertEquals(FilterReply.DENY, reply, "Message beyond threshold should be denied");
    }

    @Test
    void differentFormatsAreTrackedIndependently() {
        IntStream.range(0, ALLOWED_REPETITIONS).forEach(_ -> decide("Error {} with node id {}"));
        assertEquals(FilterReply.DENY, decide("Error {} with node id {}"));

        assertEquals(FilterReply.NEUTRAL, decide("Connection timeout for host {}"),
                "Different format should still be allowed");
    }

    @Test
    void nullFormatReturnsNeutral() {
        FilterReply reply = filter.decide(null, null, Level.ERROR, null, null, null);
        assertEquals(FilterReply.NEUTRAL, reply);
    }

    @Test
    void stopEmitsSummaryForSuppressedMessages() {
        IntStream.range(0, ALLOWED_REPETITIONS + 7).forEach(_ -> decide("Repeated error {}"));

        filter.stop();

        List<ILoggingEvent> events = listAppender.list;
        assertEquals(1, events.size(), "Should emit exactly one summary");
        String message = events.getFirst().getFormattedMessage();
        assertTrue(message.contains("7"), "Should report 7 suppressed");
        assertTrue(message.contains("Repeated error {}"), "Should include the message template");
    }

    @Test
    void summaryUsesOriginalLogLevel() {
        IntStream.range(0, ALLOWED_REPETITIONS + 3).forEach(_ -> decide("Repeated error {}"));

        filter.stop();

        ILoggingEvent event = listAppender.list.getFirst();
        assertEquals(Level.ERROR, event.getLevel(), "Summary should use the original message's log level");
    }

    @Test
    void messagesBelowLoggerLevelAreNotCounted() {
        IntStream.range(0, ALLOWED_REPETITIONS + 10)
                 .forEach(_ -> filter.decide(null, testLogger, Level.DEBUG, "Debug noise {}", null, null));

        FilterReply reply = filter.decide(null, testLogger, Level.DEBUG, "Debug noise {}", null, null);
        assertEquals(FilterReply.NEUTRAL, reply,
                "Messages below logger effective level should not be counted or suppressed");
        assertTrue(listAppender.list.isEmpty(), "No summary for messages that wouldn't be logged");
    }

    @Test
    void ownSummaryMessagesAreNeverFiltered() {
        Logger selfLogger = (Logger) LoggerFactory.getLogger(CondensingDuplicateMessageFilter.class);
        IntStream.range(0, ALLOWED_REPETITIONS + 10)
                 .forEach(_ -> filter.decide(null, selfLogger, Level.WARN, "Suppressed {} repetitions of message: {}",
                         null, null));

        FilterReply reply = filter.decide(null, selfLogger, Level.WARN, "Suppressed {} repetitions of message: {}",
                null, null);
        assertEquals(FilterReply.NEUTRAL, reply, "Filter's own messages should never be denied");
    }

    @Test
    void summaryResetsCountersSoMessagesFlowAgain() {
        IntStream.range(0, ALLOWED_REPETITIONS + 5).forEach(_ -> decide("Repeated error {}"));
        assertEquals(FilterReply.DENY, decide("Repeated error {}"), "Should still be denied before reset");

        filter.stop();
        filter.start();

        assertEquals(FilterReply.NEUTRAL, decide("Repeated error {}"),
                "After summary reset, message should be allowed again");
    }

    @Test
    void noSummaryEmittedWhenNothingWasSuppressed() {
        IntStream.range(0, ALLOWED_REPETITIONS).forEach(_ -> decide("Some message {}"));

        filter.stop();

        assertTrue(listAppender.list.isEmpty(), "No summary should be emitted when nothing was suppressed");
    }

    @Test
    void denyWorksWhenCacheNotFull() {
        IntStream.range(0, CACHE_SIZE)
                 .forEach(i -> IntStream.range(0, ALLOWED_REPETITIONS).forEach(_ -> decide("Message number " + i)));

        FilterReply reply = decide("Message number 0");
        assertEquals(FilterReply.DENY, reply, "Repeated message in cache should be denied");
    }

    @Test
    void oldestEntryIsEvictedFirst() {
        IntStream.range(0, CACHE_SIZE)
                 .forEach(i -> IntStream.range(0, ALLOWED_REPETITIONS).forEach(_ -> decide("Message number " + i)));

        decide("New message that triggers eviction");

        assertEquals(FilterReply.NEUTRAL, decide("Message number 0"),
                "Oldest entry (Message number 0) should have been evicted and treated as new");
    }

    @Test
    void evictionEmitsSummaryForSuppressedMessages() {
        IntStream.range(0, ALLOWED_REPETITIONS + 4).forEach(_ -> decide("Soon to be evicted"));
        IntStream.range(0, CACHE_SIZE - 1).forEach(i -> decide("Filler message " + i));

        decide("One more to trigger eviction");

        List<ILoggingEvent> events = listAppender.list;
        assertEquals(1, events.size(), "Should emit a summary when evicting a suppressed entry");
        String message = events.getFirst().getFormattedMessage();
        assertTrue(message.contains("4"), "Should report 4 suppressed");
        assertTrue(message.contains("Soon to be evicted"), "Should reference the evicted message");
    }

    @Test
    void bypassCondensingMarkerPreventsSuppressionAndCounting() {
        Marker bypass = MarkerFactory.getMarker(BYPASS_CONDENSING_MARKER_NAME);

        IntStream.range(0, ALLOWED_REPETITIONS + 10).forEach(_ -> {
            FilterReply reply = filter.decide(bypass, testLogger, Level.ERROR, "Time taken to process in ms 100", null,
                    null);
            assertEquals(FilterReply.NEUTRAL, reply, "Bypass-marked messages should never be denied");
        });

        filter.stop();
        assertTrue(listAppender.list.isEmpty(), "No summary should be emitted for bypass-marked messages");
    }
}
