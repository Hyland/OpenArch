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

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.CharUtils;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

/**
 * The content type a blob is stored with, and what a download may do with it.
 * <p>
 * A blob keeps the content type whoever uploaded it declared, and a download sends that value back as the
 * {@code Content-Type} header, so the value is both untrusted and load-bearing. These checks are applied where it
 * enters, rather than where it is served, so that a blob cannot be stored in a state its own download cannot
 * express.
 */
public final class ContentTypes {

    private ContentTypes() {
        // utility class
    }

    // Content types a browser executes as markup, whatever their top-level type suggests. XML is handled by
    // subtype rather than by name, see isXml. "image/svg" is the pre-2011 type, still accepted by browsers.
    private static final Collection<String> SCRIPTABLE_MIME_TYPES = List.of("image/svg", "text/html", "text/xsl");

    // Top-level types a browser renders without executing them.
    private static final Collection<String> INLINE_RENDERABLE_TYPES = List.of("image", "audio", "video", "text");

    /**
     * Checks that a caller-supplied content type can be sent back as a {@code Content-Type} header.
     * <p>
     * This parses the whole value, parameters included, with the parser that builds the download response header,
     * so that whatever is accepted here can be served later. Checking only the {@code type/subtype} name would let
     * a value through that is stored successfully and then fails on every download of that blob, which turns a bad
     * request into a blob nobody can retrieve.
     *
     * @param contentType the content type to check, which may carry parameters such as {@code ; charset=utf-8}
     * @return {@code true} if the content type can be parsed and sent back
     * @see org.springframework.http.MediaType#parseMediaType(String)
     */
    public static boolean isWellFormed(String contentType) {
        return parse(contentType) != null;
    }

    /**
     * Checks a content type and an encoding together, as the download will send them back.
     * <p>
     * A blob stores the two apart, and a download rebuilds the header by appending {@code ; charset=} and the
     * encoding to the content type, so an encoding supplied on its own still reaches the response without ever
     * passing through the content type.
     * <p>
     * The encoding is checked as a header token, not as a charset the platform knows. What must not reach the
     * header is a control character, or a separator that would end the parameter and start another one.
     *
     * @param contentType the content type to check
     * @param encoding the encoding stored alongside it, which may be {@code null} or blank
     * @return {@code true} if both can be sent back, together
     * @see com.hyland.openarch.ecm.core.io.download.DownloadHelper#getContentTypeHeader
     */
    public static boolean isWellFormed(String contentType, String encoding) {
        if (!isWellFormed(contentType)) {
            return false;
        }
        return isBlank(encoding) || isToken(encoding);
    }

    // Whether the value is a single RFC 7230 token, so that it can carry a parameter value unquoted.
    private static boolean isToken(String value) {
        return value.chars()
                    .allMatch(c -> CharUtils.isAsciiAlphanumeric((char) c) || TOKEN_PUNCTUATION.indexOf(c) >= 0);
    }

    // The non-alphanumeric half of the token grammar, https://www.rfc-editor.org/info/rfc7230/#section-3.2.6
    private static final String TOKEN_PUNCTUATION = "!#$%&'*+-.^_`|~";

    /**
     * Whether a blob of the given content type may be rendered inline by a browser, rather than only offered as a
     * download.
     * <p>
     * The content type is the uploader's, so a type a browser executes as markup (HTML, XML and SVG among them)
     * would let them run script in the context of whoever opens the blob. Such a type is served as an attachment
     * instead. Images, audio, video, PDF and plain text are safe to render and cover what inline display is for.
     *
     * @param contentType the blob's content type, which may be {@code null}
     * @return {@code true} if the content type may be rendered inline
     */
    public static boolean isSafeToRenderInline(String contentType) {
        return isSafeToRenderInline(parse(contentType), true);
    }

    /**
     * Whether a blob of the given content type may be rendered inline by a browser that was <em>not</em> told to
     * stop at the declared type.
     * <p>
     * A pre-signed URL is served by the storage provider, which can override the content type and disposition but
     * cannot add {@code X-Content-Type-Options: nosniff}. Without it a browser may look past the declared type and
     * read the body as something else, so this additionally refuses {@code text/*}: a text body is what a browser
     * most readily reads as markup, and the content type is the uploader's to choose. Images, audio, video and PDF
     * are identified by their own content and are not reinterpreted as markup.
     *
     * @param contentType the blob's content type, which may be {@code null}
     * @return {@code true} if the content type may be rendered inline without that header
     */
    public static boolean isSafeToRenderInlineWithoutNoSniff(String contentType) {
        return isSafeToRenderInline(parse(contentType), false);
    }

    private static boolean isSafeToRenderInline(MediaType mediaType, boolean noSniffSent) {
        if (mediaType == null) {
            return false;
        }
        String type = mediaType.getType().toLowerCase(Locale.ROOT);
        String subtype = mediaType.getSubtype().toLowerCase(Locale.ROOT);
        if (isXml(subtype) || SCRIPTABLE_MIME_TYPES.contains(type + '/' + subtype)) {
            // an image/* or text/* type that a browser nonetheless executes as markup
            return false;
        }
        if (!noSniffSent && "text".equals(type)) {
            return false;
        }
        return INLINE_RENDERABLE_TYPES.contains(type) || "application/pdf".equals(type + '/' + subtype);
    }

    /**
     * Whether a subtype is XML, by the structured suffix rather than by name.
     * <p>
     * A browser renders any {@code +xml} subtype as XML, so a document under an unremarkable-looking name such as
     * {@code text/custom+xml} can still carry XHTML or SVG markup and run script from it. Naming the XML types
     * individually would always be one invented subtype behind.
     */
    private static boolean isXml(String subtype) {
        return "xml".equals(subtype) || subtype.endsWith("+xml");
    }

    // The parsed content type, or {@code null} if it is absent or cannot be sent back as a header.
    private static MediaType parse(String contentType) {
        if (isBlank(contentType) || hasControlCharacter(contentType)) {
            // a control character survives inside a quoted parameter and would be written into the header
            return null;
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) { // NOSONAR a value we cannot serve is the answer, not an error
            return null;
        }
        if (mediaType.isWildcardType() || mediaType.isWildcardSubtype()) {
            // parsing accepts a wildcard, but the download builds its response with HttpHeaders#setContentType,
            // which refuses one, so a wildcard stored here would fail every later download of the blob
            return null;
        }
        return mediaType;
    }

    private static boolean hasControlCharacter(String value) {
        return value.chars().anyMatch(c -> CharUtils.isAsciiControl((char) c));
    }
}
