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
package com.hyland.openarch.ecm.core.blob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.blob.DefaultBlobDispatcher.Clause;
import com.hyland.openarch.ecm.core.blob.DefaultBlobDispatcher.Op;
import com.hyland.openarch.ecm.core.model.InternalDocument;

/**
 * Pure unit tests for the clause parsing and matching logic of {@link DefaultBlobDispatcher}.
 */
class DefaultBlobDispatcherUnitTest {

    private final DefaultBlobDispatcher dispatcher = new DefaultBlobDispatcher();

    @Test
    void getClauseParsesEveryOperator() {
        assertEquals(Op.EQ, dispatcher.getClause("p=v").op);
        assertEquals(Op.NEQ, dispatcher.getClause("p!=v").op);
        assertEquals(Op.LT, dispatcher.getClause("p<5").op);
        assertEquals(Op.LTE, dispatcher.getClause("p<=5").op);
        assertEquals(Op.GT, dispatcher.getClause("p>5").op);
        assertEquals(Op.GTE, dispatcher.getClause("p>=5").op);

        Clause eq = dispatcher.getClause("prop=val");
        assertEquals("prop", eq.xpath);
        assertEquals("val", eq.value);

        assertInstanceOf(Pattern.class, dispatcher.getClause("p~gl*b").value);
        assertEquals(Op.GLOB, dispatcher.getClause("p~gl*b").op);
        assertInstanceOf(Pattern.class, dispatcher.getClause("p^re.*").value);
        assertEquals(Op.RE, dispatcher.getClause("p^re.*").op);
    }

    @Test
    void getClauseReturnsNullForNameWithoutOperator() {
        assertNull(dispatcher.getClause("plainname"));
    }

    @Test
    void getPatternFromGlobHandlesWildcardsAndQuotesSpecials() {
        Pattern wildcards = dispatcher.getPatternFromGlob("a?c*");
        assertTrue(wildcards.matcher("abcXYZ").matches());
        assertFalse(wildcards.matcher("abXYZ").matches());

        Pattern literalDot = dispatcher.getPatternFromGlob("a.b");
        assertTrue(literalDot.matcher("a.b").matches());
        assertFalse(literalDot.matcher("aXb").matches());
    }

    @Test
    void matchEqualityOnStrings() {
        Clause eq = dispatcher.getClause("f=hello");
        assertTrue(dispatcher.match("hello", eq));
        assertFalse(dispatcher.match("world", eq));

        Clause neq = dispatcher.getClause("f!=hello");
        assertTrue(dispatcher.match("world", neq));
    }

    @Test
    void matchNumericComparisons() {
        assertTrue(dispatcher.match(5L, dispatcher.getClause("f<10")));
        assertFalse(dispatcher.match(20L, dispatcher.getClause("f<10")));
        assertTrue(dispatcher.match(10L, dispatcher.getClause("f>=10")));
        assertTrue(dispatcher.match(1.0d, dispatcher.getClause("f<1.5")));
    }

    @Test
    void matchTreatsNullAsZeroOrLiteralNull() {
        assertTrue(dispatcher.match(null, dispatcher.getClause("f=null")));
        assertTrue(dispatcher.match(null, dispatcher.getClause("f<10")));
    }

    @Test
    void matchNonNumericValueAgainstNumericField() {
        assertFalse(dispatcher.match(5L, dispatcher.getClause("f=notanumber")));
        assertTrue(dispatcher.match(5L, dispatcher.getClause("f!=notanumber")));
        assertFalse(dispatcher.match(5L, dispatcher.getClause("f<notanumber")));
    }

    @Test
    void matchInstantComparison() {
        Clause gt = dispatcher.getClause("f>2020-01-01T00:00:00Z");
        assertTrue(dispatcher.match(Instant.parse("2021-01-01T00:00:00Z"), gt));
    }

    @Test
    void matchGlobAndRegex() {
        assertTrue(dispatcher.match("hello", dispatcher.getClause("f~he*o")));
        assertTrue(dispatcher.match("hello", dispatcher.getClause("f^h.*o")));
        assertFalse(dispatcher.match("world", dispatcher.getClause("f~he*o")));
    }

    @Test
    void initializeParsesRulesAndDefault() {
        dispatcher.initialize(Map.of("prop=video", "first", "default", "other"));

        assertFalse(dispatcher.useRepositoryName);
        assertEquals("other", dispatcher.defaultProviderId);
    }

    @Test
    void initializeMissingDefaultFallsBackToRepositoryName() {
        dispatcher.initialize(Map.of("prop=x", "first")); // no default clause

        assertTrue(dispatcher.useRepositoryName);
    }

    @Test
    void initializeEmptyUsesRepositoryName() {
        dispatcher.initialize(Map.of());

        assertTrue(dispatcher.useRepositoryName);
    }

    @Test
    void getProviderIdUsesRepositoryNameByDefault() {
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.getRepositoryName()).thenReturn("myrepo");

        assertEquals("myrepo", dispatcher.getProviderId(doc, null, null));
    }
}
