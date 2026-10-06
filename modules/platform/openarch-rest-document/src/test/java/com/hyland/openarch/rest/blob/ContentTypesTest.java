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
package com.hyland.openarch.rest.blob;

import static com.hyland.openarch.ecm.core.api.impl.blob.AbstractBlob.TEXT_PLAIN;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Covers {@link ContentTypes}, the checks applied to the content type an uploader supplies for a blob
 */
class ContentTypesTest {

    @Test
    void acceptsAContentTypeThatCanBeSentBack() {
        assertTrue(ContentTypes.isWellFormed(TEXT_PLAIN));
        assertTrue(ContentTypes.isWellFormed(TEXT_PLAIN + "; charset=UTF-8"));
        assertTrue(ContentTypes.isWellFormed("application/pdf"));
        assertTrue(ContentTypes.isWellFormed("application/vnd.oasis.opendocument.text"));
        assertTrue(ContentTypes.isWellFormed("application/vnd.api+json; version=1"));
        // a parameter with no value is dropped rather than rejected, and the result still serves, so the check
        // follows that: this is about what can be sent back, not about pedantic tidiness
        assertTrue(ContentTypes.isWellFormed(TEXT_PLAIN + "; charset"));
        // well formed, but see below: being well formed is not the same as being safe to render
        assertTrue(ContentTypes.isWellFormed("image/svg+xml"));
    }

    @Test
    void refusesAContentTypeThatCannotBeSentBack() {
        assertFalse(ContentTypes.isWellFormed(null));
        assertFalse(ContentTypes.isWellFormed(""));
        assertFalse(ContentTypes.isWellFormed("   "));
        assertFalse(ContentTypes.isWellFormed("notAMimeType"));
        assertFalse(ContentTypes.isWellFormed("text/"));
        assertFalse(ContentTypes.isWellFormed("/plain"));
        assertFalse(ContentTypes.isWellFormed("text/plain/extra"));
        assertFalse(ContentTypes.isWellFormed("text\r\nX-Injected: 1/plain"));
        assertFalse(ContentTypes.isWellFormed("text /plain"));
        // characters outside the RFC 7230 token grammar, which a name-only check would have let through
        assertFalse(ContentTypes.isWellFormed("text/pl@in"));
        assertFalse(ContentTypes.isWellFormed("te(xt)/plain"));
        // malformed and unusable parameters, which are part of the value the header is built from
        assertFalse(ContentTypes.isWellFormed(TEXT_PLAIN + "; charset=\"unterminated"));
        assertFalse(ContentTypes.isWellFormed(TEXT_PLAIN + "; charset=no-such-charset"));
        // a wildcard parses, but the response header cannot be built from one
        assertFalse(ContentTypes.isWellFormed("*/*"));
        assertFalse(ContentTypes.isWellFormed("text/*"));
        // a control character survives inside a quoted parameter and would reach the header
        assertFalse(ContentTypes.isWellFormed(TEXT_PLAIN + "; name=\"a\rb\""));
        assertFalse(ContentTypes.isWellFormed(TEXT_PLAIN + "; name=\"a\u0000b\""));
    }

    /**
     * The point of {@link ContentTypes#isWellFormed}: whatever it accepts must survive being turned into the
     * response header, because that happens on every later download rather than on the upload that stored it.
     * <p>
     * The sink is {@link HttpHeaders#setContentType}, which is stricter than parsing alone: it refuses a wildcard
     * that {@link MediaType#parseMediaType} is happy to return.
     */
    @Test
    void acceptsOnlyWhatTheDownloadResponseCanBeBuiltFrom() {
        for (String contentType : new String[] { TEXT_PLAIN, TEXT_PLAIN + "; charset=UTF-8", "application/pdf",
                "application/vnd.api+json; version=1", "image/svg+xml", "text/html", "text/custom+xml" }) {
            assertTrue(ContentTypes.isWellFormed(contentType), contentType);
            assertDoesNotThrow(() -> new HttpHeaders().setContentType(MediaType.valueOf(contentType)), contentType);
        }
    }

    // The wildcards the check refuses are refused because the response header cannot carry them.
    @Test
    void refusesWhatTheDownloadResponseCannotBeBuiltFrom() {
        for (String contentType : new String[] { "*/*", "text/*" }) {
            assertFalse(ContentTypes.isWellFormed(contentType), contentType);
            HttpHeaders headers = new HttpHeaders();
            MediaType parsed = MediaType.valueOf(contentType);
            assertThrows(IllegalArgumentException.class, () -> headers.setContentType(parsed), contentType);
        }
    }

    @Test
    void allowsInlineForWhatABrowserOnlyDisplays() {
        assertTrue(ContentTypes.isSafeToRenderInline("image/png"));
        assertTrue(ContentTypes.isSafeToRenderInline("image/jpeg"));
        assertTrue(ContentTypes.isSafeToRenderInline("application/pdf"));
        assertTrue(ContentTypes.isSafeToRenderInline(TEXT_PLAIN));
        assertTrue(ContentTypes.isSafeToRenderInline(TEXT_PLAIN + "; charset=UTF-8"));
        assertTrue(ContentTypes.isSafeToRenderInline("video/mp4"));
        assertTrue(ContentTypes.isSafeToRenderInline("audio/mpeg"));
    }

    @Test
    void refusesInlineForWhatABrowserExecutes() {
        assertFalse(ContentTypes.isSafeToRenderInline("text/html"));
        assertFalse(ContentTypes.isSafeToRenderInline("text/html; charset=UTF-8"));
        assertFalse(ContentTypes.isSafeToRenderInline("TEXT/HTML"));
        assertFalse(ContentTypes.isSafeToRenderInline("text/xsl"));
        assertFalse(ContentTypes.isSafeToRenderInline("image/svg"));
        assertFalse(ContentTypes.isSafeToRenderInline("application/javascript"));
        // and anything that could not be served at all is not rendered inline either
        assertFalse(ContentTypes.isSafeToRenderInline(null));
        assertFalse(ContentTypes.isSafeToRenderInline("notAMimeType"));
        assertFalse(ContentTypes.isSafeToRenderInline("*/*"));
    }

    /**
     * A blob stores the content type and the encoding apart, and a download rejoins them, so the pair has to be
     * checked as one: an encoding supplied on its own reaches the header without ever passing through the
     * content type.
     */
    @Test
    void checksTheContentTypeAndEncodingAsThePairTheyAreSentBackAs() {
        assertTrue(ContentTypes.isWellFormed(TEXT_PLAIN, "UTF-8"));
        assertTrue(ContentTypes.isWellFormed(TEXT_PLAIN, null));
        assertTrue(ContentTypes.isWellFormed(TEXT_PLAIN, ""));
        assertTrue(ContentTypes.isWellFormed(TEXT_PLAIN + "; charset=UTF-8", "ISO-8859-1"));
        // a value that names no charset the platform knows is inert once in the header, and callers do send such
        // values (bulk import among them), so it is accepted rather than failing an upload over it
        assertTrue(ContentTypes.isWellFormed(TEXT_PLAIN, "no-such-charset"));
        assertTrue(ContentTypes.isWellFormed(TEXT_PLAIN, "encoding"));

        // what must not reach the header: a control character, or a separator that would end the parameter
        assertFalse(ContentTypes.isWellFormed(TEXT_PLAIN, "UTF-8\r\nX-Injected: 1"));
        assertFalse(ContentTypes.isWellFormed(TEXT_PLAIN, "UTF-8; evil=1"));
        assertFalse(ContentTypes.isWellFormed(TEXT_PLAIN, "\"unterminated"));
        assertFalse(ContentTypes.isWellFormed(TEXT_PLAIN, "UTF 8"));
        // and a bad content type is still refused whatever the encoding
        assertFalse(ContentTypes.isWellFormed("text/pl@in", "UTF-8"));
    }

    /**
     * Without {@code nosniff} a browser may read past the declared type, so the set that may still be rendered
     * inline shrinks to content a browser identifies from the body itself.
     */
    @Test
    void refusesInlineWithoutNoSniffForTextEvenThoughItIsOtherwiseSafe() {
        // the same values the direct download, which does send the header, renders inline
        assertTrue(ContentTypes.isSafeToRenderInline(TEXT_PLAIN));
        assertTrue(ContentTypes.isSafeToRenderInline("text/csv"));

        assertFalse(ContentTypes.isSafeToRenderInlineWithoutNoSniff(TEXT_PLAIN));
        assertFalse(ContentTypes.isSafeToRenderInlineWithoutNoSniff(TEXT_PLAIN + "; charset=UTF-8"));
        assertFalse(ContentTypes.isSafeToRenderInlineWithoutNoSniff("text/csv"));
    }

    @Test
    void allowsInlineWithoutNoSniffForContentIdentifiedByItsOwnBody() {
        assertTrue(ContentTypes.isSafeToRenderInlineWithoutNoSniff("image/png"));
        assertTrue(ContentTypes.isSafeToRenderInlineWithoutNoSniff("image/jpeg"));
        assertTrue(ContentTypes.isSafeToRenderInlineWithoutNoSniff("application/pdf"));
        assertTrue(ContentTypes.isSafeToRenderInlineWithoutNoSniff("video/mp4"));
        assertTrue(ContentTypes.isSafeToRenderInlineWithoutNoSniff("audio/mpeg"));
        // and everything the protected check refuses is refused here too
        assertFalse(ContentTypes.isSafeToRenderInlineWithoutNoSniff("text/html"));
        assertFalse(ContentTypes.isSafeToRenderInlineWithoutNoSniff("image/svg+xml"));
        assertFalse(ContentTypes.isSafeToRenderInlineWithoutNoSniff("image/anything+xml"));
        assertFalse(ContentTypes.isSafeToRenderInlineWithoutNoSniff(null));
    }

    /**
     * XML is refused by its structured {@code +xml} suffix rather than by name, because a browser renders any
     * such subtype as XML and will run script from the XHTML or SVG markup inside it.
     */
    @Test
    void refusesInlineForEveryXmlSubtype() {
        assertFalse(ContentTypes.isSafeToRenderInline("text/xml"));
        assertFalse(ContentTypes.isSafeToRenderInline("application/xml"));
        assertFalse(ContentTypes.isSafeToRenderInline("application/xhtml+xml"));
        // an image/* type that is nonetheless markup a browser runs script from
        assertFalse(ContentTypes.isSafeToRenderInline("image/svg+xml"));
        assertFalse(ContentTypes.isSafeToRenderInline("image/svg+xml; charset=UTF-8"));
        // a subtype no denylist of names would have thought to include
        assertFalse(ContentTypes.isSafeToRenderInline("text/custom+xml"));
        assertFalse(ContentTypes.isSafeToRenderInline("image/anything+xml"));
        assertFalse(ContentTypes.isSafeToRenderInline("TEXT/CUSTOM+XML"));
        // but a subtype that merely contains "xml" is not an XML document
        assertTrue(ContentTypes.isSafeToRenderInline("image/xmlish"));
    }
}
