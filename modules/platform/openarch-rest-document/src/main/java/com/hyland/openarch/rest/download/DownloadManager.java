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
package com.hyland.openarch.rest.download;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MODIFIED;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.common.utils.RFC2231;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.event.CoreEventConstants;
import com.hyland.openarch.ecm.core.api.impl.blob.FileBlob;
import com.hyland.openarch.ecm.core.api.model.BlobNotFoundException;
import com.hyland.openarch.ecm.core.api.model.PropertyNotFoundException;
import com.hyland.openarch.ecm.core.blob.BlobService;
import com.hyland.openarch.ecm.core.blob.ByteRange;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.EventContext;
import com.hyland.openarch.ecm.core.event.EventService;
import com.hyland.openarch.ecm.core.event.impl.DocumentEventContext;
import com.hyland.openarch.ecm.core.event.impl.EventContextImpl;
import com.hyland.openarch.ecm.core.event.impl.document.DownloadEvent;
import com.hyland.openarch.ecm.core.io.download.DownloadHelper;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.TypeService;
import com.hyland.openarch.rest.utils.Headers;
import com.hyland.openarch.rest.utils.Versions;
import com.hyland.openarch.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Bean allowing the download of blob to a response.
 */
@RequestScope
@Component
public class DownloadManager {

    private static final Logger log = LoggerFactory.getLogger(DownloadManager.class);

    public static final String EVENT_NAME = "download";

    public static final String CTX_EXTENDED_INFOS = "extendedInfos";

    public static final String CTX_BLOB_FILENAME = "blobFilename";

    public static final String CTX_BLOB_XPATH = "blobXPath";

    public static final String CTX_DOWNLOAD_REASON = "downloadReason";

    private final String requestMethod;

    private HttpHeaders headers;

    public DownloadManager(HttpServletRequest request) {
        this.requestMethod = request.getMethod();
        this.headers = new HttpHeaders();
        if (request.getHeaderNames() != null)
            request.getHeaderNames()
                   .asIterator()
                   .forEachRemaining(name -> this.headers.addAll(name, Collections.list(request.getHeaders(name))));
    }

    protected boolean isGet() {
        return "GET".equals(requestMethod);
    }

    protected boolean isHead() {
        return "HEAD".equals(requestMethod);
    }

    public ResponseEntity<StreamingResponseBody> download(String repoId, String docId, String xpath, String filename,
            boolean inline) throws IOException {
        try {
            CoreSession session = CoreInstance.getCoreSession(repoId);
            DocumentRef docRef = new IdRef(docId);
            if (!session.exists(docRef)) {
                throw new DocumentNotFoundException("No document found");
            }
            DocumentModel doc = session.getDocument(docRef);
            ResponseEntity<StreamingResponseBody> response = downloadBlob(doc, xpath, filename, "download", inline);
            if (response.getStatusCode().isSameCodeAs(HttpStatus.OK)) {
                BlobStreamingOutput bso = (BlobStreamingOutput) response.getBody();
                if (bso != null) {
                    fireDownloadUsageEvent(bso.getBlob(), false);
                }
            }
            return response;
        } catch (DocumentNotFoundException | BlobNotFoundException e) {
            throw e;
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    protected ResponseEntity<StreamingResponseBody> downloadBlob(DocumentModel doc, String xpath, String filename,
            String reason, boolean inline) throws IOException {
        HttpStatus responseStatus = isHead() ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        if (doc == null) {
            throw new DocumentNotFoundException("No doc specified");
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        Blob blob = resolveBlob(doc, xpath);
        checkBlob(blob);
        if (filename == null) {
            filename = blob.getFilename();
        }
        Map<String, Serializable> extendedInfos = new HashMap<>();
        Instant lastModified = null;
        if (doc != null) {
            try {
                lastModified = (Instant) doc.getPropertyValue(SYS_MODIFIED);
            } catch (PropertyNotFoundException | ClassCastException e) {
                // ignore
            }
        }

        // check blob permissions
        if (!checkPermission(doc, xpath, blob, reason, extendedInfos)) {
            throw new DocumentSecurityException("Permission denied");
        }

        try {
            String contentType = blob.getMimeType();
            if (contentType == null) {
                log.info("Null content type on blob for document with id '{}', property='{}'", doc.getId(), xpath);
            }
            responseHeaders.setContentType(contentType != null ? MediaType.valueOf(contentType)
                    : MediaType.valueOf(Versions.APP_HXCS_OCTET_STREAM_V1));
            long length = blob.getLength();
            ByteRange byteRange = getByteRange(length);

            String digest = blob.getDigest();
            String digestAlgorithm = blob.getDigestAlgorithm();
            if (digest == null) {
                log.error("Null digest on blob for document with id '{}', property='{}'", doc.getId(), xpath);
                throw new RepositoryException("Null digest");
            }

            // Want-Digest / Digest
            Set<String> wantDigests = getWantDigests();
            if (!wantDigests.isEmpty() && wantDigests.contains(digestAlgorithm.toLowerCase())) {
                // Digest header (RFC3230)
                responseHeaders.add(Headers.DIGEST, digestAlgorithm + '=' + hexToBase64(digest));
            }

            CacheControl cacheControl = getCacheControlHeaders();

            // If-Modified-Since / Last-Modified
            if (lastModified != null) {
                long lastModifiedMillis = lastModified.toEpochMilli();
                responseHeaders.add(Headers.LAST_MODIFIED, String.valueOf(lastModifiedMillis));
                long ifModifiedSince = getIfModifiedSince();
                if (ifModifiedSince != -1 && ifModifiedSince >= lastModifiedMillis) {
                    // not modified
                    return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                                         .cacheControl(cacheControl)
                                         .headers(responseHeaders)
                                         .build();
                }
            }

            // If-None-Match / ETag
            String etag = '"' + digest + '"'; // with quotes per RFC7232 2.3
            responseHeaders.add(Headers.ETAG, etag); // re-send even on SC_NOT_MODIFIED
            String ifNoneMatch = headers.getFirst(Headers.IF_NONE_MATCH);
            if (ifNoneMatch != null) {
                boolean match = false;
                if (ifNoneMatch.equals("*")) {
                    match = true;
                } else {
                    for (String previousEtag : StringUtils.split(ifNoneMatch, ", ")) {
                        if (previousEtag.equals(etag)) {
                            match = true;
                            break;
                        }
                    }
                }
                if (match) {
                    if (isGet() || isHead()) {
                        responseStatus = HttpStatus.NOT_MODIFIED;
                    } else {
                        // per RFC7232 3.2
                        responseStatus = HttpStatus.PRECONDITION_FAILED;
                    }
                    return ResponseEntity.status(responseStatus)
                                         .cacheControl(cacheControl)
                                         .headers(responseHeaders)
                                         .eTag(etag)
                                         .build();
                }
            }

            // regular processing

            if (StringUtils.isBlank(filename)) {
                filename = StringUtils.defaultIfBlank(blob.getFilename(), "file");
            }
            String contentDisposition = getRFC2231ContentDisposition(filename, inline);
            responseHeaders.add(Headers.CONTENT_DISPOSITION, contentDisposition);

            responseHeaders.add(Headers.ACCEPT_RANGES, "bytes");
            if (byteRange != null) {
                responseHeaders.add(Headers.CONTENT_RANGE,
                        "bytes " + byteRange.getStart() + "-" + byteRange.getEnd() + "/" + length);
                responseStatus = HttpStatus.PARTIAL_CONTENT;
            }
            long contentLength = byteRange == null ? length : byteRange.getLength();
            responseHeaders.add(Headers.CONTENT_LENGTH, String.valueOf(contentLength));

            // log the download but not if it's a random byte range
            if (byteRange == null || byteRange.getStart() == 0) {
                logDownload(doc, xpath, filename, reason, extendedInfos, false);
            }

            if (!isHead()) {
                // Only try to get the blob URL when the request is a GET method
                if (isGet()) {
                    URI uri = Framework.getService(BlobService.class)
                                       .getURI(blob, BlobService.UsageHint.DOWNLOAD, -1, null);
                    if (uri != null) {
                        // check Blob Manager external download link (servlet request is not used)
                        try (InputStream is = uri.toURL().openStream()) {
                            // execute the final download from url
                            return ResponseEntity.status(responseStatus)
                                                 .headers(responseHeaders)
                                                 .body(new BlobStreamingOutput(
                                                         new FileBlob(is, contentType, blob.getEncoding()), byteRange));
                        }
                    }
                }

                // execute the final download
                return transferBlobWithByteRange(blob, byteRange, responseHeaders, responseStatus);

            }
        } catch (UncheckedIOException e) {
            DownloadHelper.handleClientDisconnect(e.getCause());
        }

        return ResponseEntity.status(responseStatus).headers(responseHeaders).build();
    }

    private long getIfModifiedSince() {
        long ifModifiedSince;
        try {
            ifModifiedSince = Long.valueOf(headers.getFirst(Headers.IF_MODIFIED_SINCE));
        } catch (IllegalArgumentException e) {
            log.debug("Invalid {} header", Headers.IF_MODIFIED_SINCE, e);
            ifModifiedSince = -1;
        }
        return ifModifiedSince;
    }

    public ResponseEntity<String> generatePresignedUrl(String repoId, String docId, String xpath, String filename,
            Long expirationSeconds) throws IOException {
        return generatePresignedUrl(repoId, docId, xpath, filename, expirationSeconds, false);
    }

    public ResponseEntity<String> generatePresignedUrl(String repoId, String docId, String xpath, String filename,
            Long expirationSeconds, boolean inline) throws IOException {
        CoreSession session = CoreInstance.getCoreSession(repoId);
        DocumentRef docRef = new IdRef(docId);
        if (!session.exists(docRef)) {
            throw new DocumentNotFoundException("No document found");
        }
        DocumentModel doc = session.getDocument(docRef);
        Blob blob = resolveBlob(doc, xpath);
        checkBlob(blob);
        if (filename == null) {
            filename = blob.getFilename();
        }
        if (expirationSeconds == null) {
            expirationSeconds = -1L; // means use the default
        }
        URI uri = Framework.getService(BlobService.class)
                           .getURI(blob, BlobService.UsageHint.DOWNLOAD, expirationSeconds, inline, null);
        if (uri == null) {
            throw new RepositoryException("Pre-signed URL not available for this blob provider",
                    HttpStatus.NOT_IMPLEMENTED.value());
        }
        // log the download on successful pre-signed url generation
        logDownload(doc, xpath, filename, "download", new HashMap<>(), true);
        fireDownloadUsageEvent(blob, true);
        return ResponseEntity.status(HttpStatus.OK).body(uri.toString());
    }

    private static void checkBlob(Blob blob) {
        if (blob == null) {
            throw new BlobNotFoundException("No blob found");
        }
    }

    protected ByteRange getByteRange(long length) {
        String range = headers.getFirst(Headers.RANGE);
        if (StringUtils.isBlank(range)) {
            return null;
        }
        ByteRange byteRange = DownloadHelper.parseRange(range, length);
        if (byteRange == null) {
            log.debug("Invalid byte range received: {}", range);
        }
        return byteRange;
    }

    protected Set<String> getWantDigests() {
        List<String> values = headers.get(Headers.WANT_DIGEST);
        if (values == null) {
            return Collections.emptySet();
        }
        Set<String> wantDigests = new HashSet<>();
        for (String value : values) {
            int semicolon = value.indexOf(';');
            if (semicolon >= 0) {
                value = value.substring(0, semicolon);
            }
            wantDigests.add(value.trim().toLowerCase());
        }
        return wantDigests;
    }

    protected static String hexToBase64(String hexString) {
        try {
            return Base64.encodeBase64String(Hex.decodeHex(hexString.toCharArray()));
        } catch (DecoderException e) {
            throw new RepositoryException(e);
        }
    }

    protected String getRFC2231ContentDisposition(String filename, boolean inline) {
        return RFC2231.encodeContentDisposition(filename, inline);
    }

    protected Blob resolveBlob(DocumentModel doc, String xpath) {
        Blob blob;
        try {
            blob = (Blob) doc.getPropertyValue(xpath);
        } catch (PropertyNotFoundException e) {
            log.debug("Property '{}' not found", xpath, e);
            return null;
        } catch (ClassCastException e) {
            log.debug("Property '{}' is not a blob", xpath, e);
            return null;
        }
        return blob;
    }

    protected CacheControl getCacheControlHeaders() {
        return CacheControl.maxAge(15, TimeUnit.SECONDS).mustRevalidate();
    }

    /**
     * Checks whether the download of the blob is allowed.
     *
     * @param doc the doc for which this download occurs, if available
     * @param xpath the blob's xpath, if available
     * @param blob the blob
     * @param reason the download reason
     * @param extendedInfos an optional map of extended informations to log
     * @return {@code true} if download is allowed
     */
    protected boolean checkPermission(DocumentModel doc, String xpath, Blob blob, String reason,
            Map<String, Serializable> extendedInfos) {
        // TODO plug additional download permission logic here
        return true;
    }

    protected void logDownload(DocumentModel doc, String xpath, String filename, String reason,
            Map<String, Serializable> extendedInfos, boolean isPresignedUrl) {
        if (isHead()) {
            // don't log HEAD requests
            return;
        }
        EventService eventService = Framework.getService(EventService.class);
        if (eventService == null) {
            return;
        }
        EventContext ctx;
        if (doc != null) {
            CoreSession session = doc.getCoreSession();
            RepositoryPrincipal principal = session == null ? RepositoryPrincipal.getCurrent() : session.getPrincipal();
            ctx = new DocumentEventContext(session, principal, doc);
            ctx.setProperty(CoreEventConstants.REPOSITORY_NAME, doc.getRepositoryName());
            ctx.setProperty(CoreEventConstants.IS_PRESIGNED_URL, isPresignedUrl);
        } else {
            ctx = new EventContextImpl(null, RepositoryPrincipal.getCurrent());
        }
        Map<String, Serializable> map = new HashMap<>();
        map.put(CTX_BLOB_XPATH, xpath);
        map.put(CTX_BLOB_FILENAME, filename);
        map.put(CTX_DOWNLOAD_REASON, reason);
        if (extendedInfos != null) {
            map.putAll(extendedInfos);
        }
        ctx.setProperty(CTX_EXTENDED_INFOS, (Serializable) map);
        ctx.setProperty("comment", filename);
        Event event = ctx.newEvent(EVENT_NAME);
        eventService.fireEvent(event);
    }

    protected ResponseEntity<StreamingResponseBody> transferBlobWithByteRange(Blob blob, ByteRange byteRange,
            HttpHeaders headers, HttpStatus responseStatus) {

        return ResponseEntity.status(responseStatus).headers(headers).body(new BlobStreamingOutput(blob, byteRange));
    }

    protected void fireDownloadUsageEvent(Blob blob, boolean isPresignedUrl) {
        DownloadEvent downloadEvent = new DownloadEvent(blob.getLength(), blob.getMimeType(),
                isPresignedUrl);
        downloadEvent.fire();
    }

    public class BlobStreamingOutput implements StreamingResponseBody {

        private final Blob blob;

        private final ByteRange byteRange;

        public BlobStreamingOutput(Blob blob, ByteRange byteRange) {
            this.blob = blob;
            this.byteRange = byteRange;
        }

        @Override
        public void writeTo(OutputStream out) {
            try (InputStream in = blob.getStream()) {
                // Spring MVC will flush and close the stream
                // The servlet container will automatically close the stream
                // after the servlet is finished running as part of the servlet request life-cycle.
                if (byteRange == null) {
                    in.transferTo(out);
                } else {
                    InputStream substream = byteRange.forStream(in);
                    // don't use IOUtils.copyLarge because it uses a skip method that reads
                    // all intervening bytes, which is inefficient for skippable streams
                    substream.transferTo(out);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public Blob getBlob() {
            return blob;
        }

        public ByteRange getByteRange() {
            return byteRange;
        }

    }

    record Result(String xpath, String filename) {
    }

    public static Result getXPathAndFilename(String propertyXPathAndFilename, String repoId) {
        propertyXPathAndFilename = propertyXPathAndFilename.charAt(0) == '/' ? propertyXPathAndFilename.substring(1)
                : propertyXPathAndFilename;
        String xpath = propertyXPathAndFilename;
        String filename = null;
        SchemaManager schemaManager = Framework.getService(TypeService.class).getSchemaManager(repoId);
        if (schemaManager.getField(propertyXPathAndFilename) == null) {
            int lastSlash = propertyXPathAndFilename.lastIndexOf("/");
            if (lastSlash > -1) {
                xpath = propertyXPathAndFilename.substring(0, lastSlash);
                filename = propertyXPathAndFilename.substring(lastSlash + 1);
            }
        }
        return new Result(xpath, filename);
    }

}
