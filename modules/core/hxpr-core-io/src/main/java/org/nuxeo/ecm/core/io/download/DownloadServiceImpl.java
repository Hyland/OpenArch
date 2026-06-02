/*
 * (C) Copyright 2015-2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 *     Estelle Giuly <egiuly@nuxeo.com>
 */
package org.nuxeo.ecm.core.io.download;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MODIFIED;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.URIUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.blob.BlobService;
import org.nuxeo.ecm.core.blob.BlobService.UsageHint;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ByteRange;
import org.nuxeo.ecm.core.blob.LocalBlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.core.io.NginxConstants;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * This service allows the download of blobs to a HTTP response.
 *
 * @since 7.3
 */
public class DownloadServiceImpl extends DefaultComponent implements DownloadService {

    private static final Logger log = LogManager.getLogger(DownloadServiceImpl.class);

    public static final String XP_REDIRECT_RESOLVER = "redirectResolver";

    protected static final int DOWNLOAD_BUFFER_SIZE = 1024 * 512;

    public static final String NUXEO_VIRTUAL_HOST = "nuxeo-virtual-host";

    public static final String VH_PARAM = "nuxeo.virtual.host";

    public static final String FORCE_NO_CACHE_ON_MSIE = "org.nuxeo.download.force.nocache.msie";

    /**
     * Property that enables returning blob URLs with redirections already taken into account. Only applies to code
     * calling the DownloadService.getFullDownloadUrl API.
     *
     * @since 11.1
     */
    public static final String DOWNLOAD_URL_FOLLOW_REDIRECT = "org.nuxeo.download.url.follow.redirect";

    private static final Pattern FILENAME_SANITIZATION_REGEX = Pattern.compile(";\\w+=.*");

    public static final String SHA256 = "SHA-256";

    public static final String CTX_EXTENDED_INFOS = "extendedInfos";

    public static final String CTX_BLOB_FILENAME = "blobFilename";

    public static final String CTX_BLOB_XPATH = "blobXPath";

    public static final String CTX_DOWNLOAD_REASON = "downloadReason";

    protected enum Action {
        DOWNLOAD, DOWNLOAD_FROM_DOC, INFO
    }

    protected RedirectResolver redirectResolver;

    @Override
    public void start() {
        Optional<RedirectResolverDescriptor> optDesc = getRegistryContribution(XP_REDIRECT_RESOLVER);
        if (optDesc.isPresent()) {
            RedirectResolverDescriptor descriptor = optDesc.get();
            try {
                Class<?> klass = Class.forName(descriptor.klass);
                // dynamic class check, the generics aren't enough
                if (!RedirectResolver.class.isAssignableFrom(klass)) {
                    throw new RuntimeException("Class does not implement RedirectResolver: " + klass.getName());
                }
                redirectResolver = (RedirectResolver) klass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                log.error("Unable to instantiate redirectResolver", e);
            }
        }
        if (redirectResolver == null) {
            redirectResolver = new DefaultRedirectResolver();
        }
    }

    @Override
    public void stop() throws InterruptedException {
        redirectResolver = null;
    }

    /**
     * {@inheritDoc} Multipart download are not yet supported. You can only provide a blob singleton at this time.
     */
    @Override
    public String storeBlobs(List<Blob> blobs) {
        if (blobs.size() > 1) {
            throw new IllegalArgumentException("multipart download not yet implemented");
        }
        TransientStore ts = Framework.getService(TransientStoreService.class).getStore(TRANSIENT_STORE_STORE_NAME);
        String storeKey = UUID.randomUUID().toString();
        ts.putBlobs(storeKey, blobs);
        ts.setCompleted(storeKey, true);
        return storeKey;
    }

    @Override
    public String getFullDownloadUrl(DocumentModel doc, String xpath, Blob blob, String baseUrl) {
        if (Framework.isBooleanPropertyTrue(DOWNLOAD_URL_FOLLOW_REDIRECT)) {
            try {
                URI uri = redirectResolver.getURI(blob, UsageHint.DOWNLOAD, -1, null);
                if (uri != null) {
                    return uri.toString();
                }
            } catch (IOException e) {
                log.error(e, e);
            }
        }
        return baseUrl + getDownloadUrl(doc, xpath, blob.getFilename());
    }

    @Override
    public String getDownloadUrl(DocumentModel doc, String xpath, String filename) {
        return getDownloadUrl(doc.getRepositoryName(), doc.getId(), xpath, filename, doc.getChangeToken());
    }

    @Override
    public String getDownloadUrl(String repositoryName, String docId, String xpath, String filename) {
        return getDownloadUrl(repositoryName, docId, xpath, filename, null);
    }

    @Override
    public String getDownloadUrl(String repositoryName, String docId, String xpath, String filename,
            String changeToken) {
        StringBuilder sb = new StringBuilder();
        sb.append(NXFILE);
        sb.append("/").append(repositoryName);
        sb.append("/").append(docId);
        if (xpath != null) {
            sb.append("/").append(xpath);
            if (filename != null) {
                // make sure filename doesn't contain path separators
                filename = getSanitizedFilenameWithoutPath(filename);
                sb.append("/").append(URIUtils.quoteURIPathComponent(filename, true));
            }
        }
        if (StringUtils.isNotEmpty(changeToken)) {
            try {
                sb.append("?")
                  .append(CoreSession.CHANGE_TOKEN)
                  .append("=")
                  .append(URLEncoder.encode(changeToken, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                log.error("Cannot append changeToken", e);
            }
        }
        return sb.toString();
    }

    protected String getSanitizedFilenameWithoutPath(String filename) {
        int sep = Math.max(filename.lastIndexOf('\\'), filename.lastIndexOf('/'));
        if (sep != -1) {
            filename = filename.substring(sep + 1);
        }

        return FILENAME_SANITIZATION_REGEX.matcher(filename).replaceAll("");
    }

    @Override
    public String getDownloadUrl(String storeKey) {
        return NXBIGBLOB + "/" + storeKey;
    }

    /**
     * Gets the download path and action of the URL to use to download blobs. For instance, from the path
     * "nxfile/default/3727ef6b-cf8c-4f27-ab2c-79de0171a2c8/sysfiles_blob/0/blob/image.png", the pair
     * ("default/3727ef6b-cf8c-4f27-ab2c-79de0171a2c8/sysfiles_blob/0/blob/image.png", Action.DOWNLOAD_FROM_DOC) is
     * returned.
     *
     * @param path the path of the URL to use to download blobs
     * @return the pair download path and action
     * @since 9.1
     */
    protected Pair<String, Action> getDownloadPathAndAction(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        int slash = path.indexOf('/');
        if (slash < 0) {
            return null;
        }

        // remove query string if any
        path = path.replaceFirst("\\?.*$", "");

        String type = path.substring(0, slash);
        String downloadPath = path.substring(slash + 1);
        switch (type) {
        case NXDOWNLOADINFO:
            // used by nxdropout.js
            return Pair.of(downloadPath, Action.INFO);
        case NXFILE:
            return Pair.of(downloadPath, Action.DOWNLOAD_FROM_DOC);
        case NXBIGBLOB:
            return Pair.of(downloadPath, Action.DOWNLOAD);
        default:
            return null;
        }
    }

    @Override
    public Blob resolveBlobFromDownloadUrl(String downloadURL) {
        Pair<String, Action> pair = getDownloadPathAndAction(downloadURL);
        if (pair == null) {
            return null;
        }
        String downloadPath = pair.getLeft();
        try {
            DownloadBlobInfo downloadBlobInfo = new DownloadBlobInfo(downloadPath);
            CoreSession session = CoreInstance.getCoreSession(downloadBlobInfo.repository);
            DocumentRef docRef = new IdRef(downloadBlobInfo.docId);
            if (!session.exists(docRef)) {
                return null;
            }
            DocumentModel doc = session.getDocument(docRef);
            Blob blob = resolveBlob(doc, downloadBlobInfo.xpath);
            if (!checkPermission(doc, downloadBlobInfo.xpath, blob, null, null)) {
                return null;
            }
            return blob;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void handleDownload(HttpServletRequest req, HttpServletResponse resp, String baseUrl, String path)
            throws IOException {
        Pair<String, Action> pair = getDownloadPathAndAction(path);
        if (pair == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid URL syntax");
            return;
        }
        String downloadPath = pair.getLeft();
        Action action = pair.getRight();
        switch (action) {
        case INFO:
            handleDownload(req, resp, downloadPath, baseUrl, true);
            break;
        case DOWNLOAD_FROM_DOC:
            handleDownload(req, resp, downloadPath, baseUrl, false);
            break;
        case DOWNLOAD:
            downloadBlob(req, resp, downloadPath, "download");
            break;
        default:
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid URL syntax");
        }
    }

    protected static boolean isHead(HttpServletRequest request) {
        return "HEAD".equals(request.getMethod());
    }

    protected void handleDownload(HttpServletRequest req, HttpServletResponse resp, String downloadPath, String baseUrl,
            boolean info) throws IOException {
        boolean tx = false;
        DownloadBlobInfo downloadBlobInfo;
        try {
            downloadBlobInfo = new DownloadBlobInfo(downloadPath);
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid URL syntax");
            return;
        }

        try {
            if (!TransactionHelper.isTransactionActive()) {
                // Manually start and stop a transaction around repository access to be able to release transactional
                // resources without waiting for the download that can take a long time (longer than the transaction
                // timeout) especially if the client or the connection is slow.
                tx = TransactionHelper.startTransaction();
            }
            String xpath = downloadBlobInfo.xpath;
            String filename = downloadBlobInfo.filename;
            CoreSession session = CoreInstance.getCoreSession(downloadBlobInfo.repository);
            DocumentRef docRef = new IdRef(downloadBlobInfo.docId);
            if (!session.exists(docRef)) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No document found");
                return;
            }
            DocumentModel doc = session.getDocument(docRef);
            if (info) {
                Blob blob = resolveBlob(doc, xpath);
                if (blob == null) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No blob found");
                    return;
                }
                String downloadUrl = baseUrl + getDownloadUrl(doc, xpath, filename);
                String result = blob.getMimeType() + ':' + URLEncoder.encode(blob.getFilename(), "UTF-8") + ':'
                        + downloadUrl;
                resp.setContentType("text/plain");
                if (!isHead(req)) {
                    resp.getWriter().write(result);
                    resp.getWriter().flush();
                }
            } else {
                DownloadContext context = DownloadContext.builder(req, resp)
                                                         .doc(doc)
                                                         .xpath(xpath)
                                                         .filename(filename)
                                                         .reason("download")
                                                         .build();
                downloadBlob(context);
            }
        } catch (NuxeoException e) {
            if (tx) {
                TransactionHelper.setTransactionRollbackOnly();
            }
            throw new IOException(e);
        } finally {
            if (tx) {
                TransactionHelper.commitOrRollbackTransaction();
            }
        }
    }

    @Override
    public void downloadBlob(HttpServletRequest request, HttpServletResponse response, String key, String reason)
            throws IOException {
        TransientStore ts = Framework.getService(TransientStoreService.class).getStore(TRANSIENT_STORE_STORE_NAME);
        if (!ts.exists(key)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        List<Blob> blobs = ts.getBlobs(key);
        if (blobs == null || blobs.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (blobs.size() > 1) {
            throw new IllegalArgumentException("multipart download not yet implemented");
        }
        if (ts.getParameter(key, TRANSIENT_STORE_PARAM_ERROR) != null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    (String) ts.getParameter(key, TRANSIENT_STORE_PARAM_ERROR));
            return;
        }
        boolean isCompleted = ts.isCompleted(key);
        if (!isCompleted) {
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            return;
        }
        Blob blob = blobs.get(0);
        try {
            DownloadContext context = DownloadContext.builder(request, response).blob(blob).reason(reason).build();
            downloadBlob(context);
        } finally {
            if (!isHead(request)) {
                ts.remove(key);
            }
        }
    }

    @Override
    public void downloadBlob(DownloadContext context) throws IOException {
        HttpServletRequest request = context.getRequest();
        HttpServletResponse response = context.getResponse();
        DocumentModel doc = context.getDocumentModel();
        String xpath = context.getXPath();
        Blob blob = context.getBlob();
        if (blob == null) {
            if (doc == null) {
                throw new NuxeoException("No doc specified");
            }
            blob = resolveBlob(doc, xpath);
            if (blob == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No blob found");
                return;
            }
        }
        String filename = context.getFilename();
        if (filename == null) {
            filename = blob.getFilename();
        }
        String reason = context.getReason();
        String requestReason = (String) request.getAttribute(REQUEST_ATTR_DOWNLOAD_REASON);
        if (requestReason != null) {
            reason = requestReason;
        }
        Map<String, Serializable> extendedInfos = context.getExtendedInfos();
        extendedInfos = extendedInfos == null ? new HashMap<>() : new HashMap<>(extendedInfos);
        String requestRendition = (String) request.getAttribute(REQUEST_ATTR_DOWNLOAD_RENDITION);
        if (requestRendition != null) {
            extendedInfos.put(EXTENDED_INFO_RENDITION, requestRendition);
        }
        Boolean inline = context.getInline();
        Consumer<ByteRange> blobTransferer = context.getBlobTransferer();
        if (blobTransferer == null) {
            Blob fblob = blob;
            blobTransferer = byteRange -> transferBlobWithByteRange(fblob, byteRange, response);
        }
        Instant lastModified = context.getLastModified();
        if (lastModified == null && doc != null) {
            try {
                lastModified = (Instant) doc.getPropertyValue(SYS_MODIFIED);
            } catch (PropertyNotFoundException | ClassCastException e) {
                // ignore
            }
        }

        // check blob permissions
        if (!checkPermission(doc, xpath, blob, reason, extendedInfos)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Permission denied");
            return;
        }

        // check Blob Manager external download link
        URI uri = redirectResolver.getURI(blob, UsageHint.DOWNLOAD, -1, request);
        if (uri != null) {
            try {
                extendedInfos.put("redirect", uri.toString());
                logDownload(request, doc, xpath, filename, reason, extendedInfos, false);
                response.sendRedirect(uri.toString());
            } catch (IOException ioe) {
                DownloadHelper.handleClientDisconnect(ioe);
            }
            return;
        }

        try {
            String contentType = blob.getMimeType();
            // empty is true for an unavailable lazy rendition
            boolean empty = contentType != null && contentType.contains("empty=true");

            long length = blob.getLength();
            ByteRange byteRange = getByteRange(request, length);

            String digest = blob.getDigest();
            String digestAlgorithm = blob.getDigestAlgorithm();
            if (digest == null) {
                digest = DigestUtils.sha256Hex(blob.getStream());
                digestAlgorithm = SHA256;
            }

            // Want-Digest / Digest
            Set<String> wantDigests = getWantDigests(request);
            if (!wantDigests.isEmpty() && wantDigests.contains(digestAlgorithm.toLowerCase())) {
                // Digest header (RFC3230)
                response.setHeader("Digest", digestAlgorithm + '=' + hexToBase64(digest));
            }

            addCacheControlHeaders(request, response);

            // If-Modified-Since / Last-Modified
            if (!empty && lastModified != null) {
                long lastModifiedMillis = lastModified.toEpochMilli();
                response.setDateHeader("Last-Modified", lastModifiedMillis);
                long ifModifiedSince;
                try {
                    ifModifiedSince = request.getDateHeader("If-Modified-Since");
                } catch (IllegalArgumentException e) {
                    log.debug("Invalid If-Modified-Since header", e);
                    ifModifiedSince = -1;
                }
                if (ifModifiedSince != -1 && ifModifiedSince >= lastModifiedMillis) {
                    // not modified
                    response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
                    return;
                }
            }

            // If-None-Match / ETag
            if (!empty) {
                String etag = '"' + digest + '"'; // with quotes per RFC7232 2.3
                response.setHeader("ETag", etag); // re-send even on SC_NOT_MODIFIED
                String ifNoneMatch = request.getHeader("If-None-Match");
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
                        String method = request.getMethod();
                        if (method.equals("GET") || method.equals("HEAD")) {
                            response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
                        } else {
                            // per RFC7232 3.2
                            response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                        }
                        return;
                    }
                }
            }

            // regular processing

            if (StringUtils.isBlank(filename)) {
                filename = StringUtils.defaultIfBlank(blob.getFilename(), "file");
            }
            String contentDisposition = DownloadHelper.getRFC2231ContentDisposition(request, filename, inline);
            response.setHeader("Content-Disposition", contentDisposition);
            response.setContentType(contentType);
            if (StringUtils.isNotBlank(blob.getEncoding())) {
                try {
                    response.setCharacterEncoding(blob.getEncoding());
                } catch (IllegalArgumentException e) {
                    // ignore invalid encoding
                }
            }

            response.setHeader("Accept-Ranges", "bytes");
            if (byteRange != null) {
                response.setHeader("Content-Range",
                        "bytes " + byteRange.getStart() + "-" + byteRange.getEnd() + "/" + length);
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            }
            long contentLength = byteRange == null ? length : byteRange.getLength();
            response.setContentLengthLong(contentLength);

            // log the download but not if it's a random byte range
            if (byteRange == null || byteRange.getStart() == 0) {
                logDownload(request, doc, xpath, filename, reason, extendedInfos, false);
            }

            String xAccelLocation = request.getHeader(NginxConstants.X_ACCEL_LOCATION_HEADER);
            if (Framework.isBooleanPropertyTrue(NginxConstants.X_ACCEL_ENABLED)
                    && StringUtils.isNotEmpty(xAccelLocation)) {
                File file = blob.getFile();
                if (file != null && blob instanceof ManagedBlob) {
                    File storageDir;
                    BlobService blobService = Framework.getService(BlobService.class);
                    BlobProvider blobProvider = blobService.getBlobProvider(blob);
                    if (blobProvider instanceof LocalBlobProvider localBlobProvider) {
                        storageDir = localBlobProvider.getStorageDir().toFile();
                    } else {
                        throw new NuxeoException("Cannot use Nginx accelerated download with blob provider: "
                                + blobProvider.getClass().getName());
                    }
                    String relative = storageDir.toURI().relativize(file.toURI()).getPath();
                    if (xAccelLocation.endsWith("/")) {
                        xAccelLocation = xAccelLocation + relative;
                    } else {
                        xAccelLocation = xAccelLocation + "/" + relative;
                    }
                    response.setHeader(NginxConstants.X_ACCEL_REDIRECT_HEADER, xAccelLocation);
                    return;
                }
            }

            if (!isHead(request)) {
                // execute the final download
                blobTransferer.accept(byteRange);
            }
        } catch (UncheckedIOException e) {
            DownloadHelper.handleClientDisconnect(e.getCause());
        } catch (IOException ioe) {
            DownloadHelper.handleClientDisconnect(ioe);
        }
    }

    protected ByteRange getByteRange(HttpServletRequest request, long length) {
        String range = request.getHeader("Range");
        if (StringUtils.isBlank(range)) {
            return null;
        }
        ByteRange byteRange = DownloadHelper.parseRange(range, length);
        if (byteRange == null) {
            log.debug("Invalid byte range received: {}", range);
        }
        return byteRange;
    }

    protected Set<String> getWantDigests(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders("Want-Digest");
        if (values == null) {
            return Collections.emptySet();
        }
        Set<String> wantDigests = new HashSet<>();
        for (String value : Collections.list(values)) {
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
            throw new NuxeoException(e);
        }
    }

    protected void transferBlobWithByteRange(Blob blob, ByteRange byteRange, HttpServletResponse response) {
        transferBlobWithByteRange(blob, byteRange, () -> {
            try {
                return response.getOutputStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        try {
            response.flushBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void transferBlobWithByteRange(Blob blob, ByteRange byteRange, Supplier<OutputStream> outputStreamSupplier) {
        try (InputStream in = blob.getStream()) {
            @SuppressWarnings("resource")
            OutputStream out = outputStreamSupplier.get(); // not ours to close
            BufferingServletOutputStream.stopBuffering(out);
            if (byteRange == null) {
                in.transferTo(out);
            } else {
                @SuppressWarnings("resource") // closing the original stream is enough
                InputStream substream = byteRange.forStream(in);
                // don't use IOUtils.copyLarge because it uses a skip method that reads
                // all intervening bytes, which is inefficient for skippable streams
                substream.transferTo(out);
            }
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Blob resolveBlob(DocumentModel doc) {
        return resolveBlob(doc, null);
    }

    @Override
    public Blob resolveBlob(DocumentModel doc, String xpath) {
        if (xpath == null) {
            xpath = SYSFILE_BLOB;
        }
        try {
            return (Blob) doc.getPropertyValue(xpath);
        } catch (PropertyNotFoundException e) {
            log.debug("Property '{}' not found", xpath, e);
            return null;
        }
    }

    @Override
    public boolean checkPermission(DocumentModel doc, String xpath, Blob blob, String reason,
            Map<String, Serializable> extendedInfos) {
        // TODO plug additional download permission logic here
        return true;
    }

    /**
     * Internet Explorer file downloads over SSL do not work with certain HTTP cache control headers
     * <p>
     * See http://support.microsoft.com/kb/323308/
     * <p>
     * What is not mentioned in the above Knowledge Base is that "Pragma: no-cache" also breaks download in MSIE over
     * SSL
     */
    protected void addCacheControlHeaders(HttpServletRequest request, HttpServletResponse response) {
        String userAgent = request.getHeader("User-Agent");
        boolean secure = request.isSecure();
        if (!secure) {
            String nvh = request.getHeader(NUXEO_VIRTUAL_HOST);
            if (nvh == null) {
                nvh = Framework.getProperty(VH_PARAM);
            }
            if (nvh != null) {
                secure = nvh.startsWith("https");
            }
        }
        if (userAgent != null && userAgent.contains("MSIE") && (secure || forceNoCacheOnMSIE())) {
            String cacheControl = "max-age=15, must-revalidate";
            log.debug("Setting Cache-Control: {}", cacheControl);
            response.setHeader("Cache-Control", cacheControl);
        }
    }

    protected static boolean forceNoCacheOnMSIE() {
        // see NXP-7759
        return Framework.isBooleanPropertyTrue(FORCE_NO_CACHE_ON_MSIE);
    }

    @Override
    public void logDownload(HttpServletRequest request, DocumentModel doc, String xpath, String filename, String reason,
            Map<String, Serializable> extendedInfos, boolean isPresignedUrl) {
        if (request != null && isHead(request)) {
            // don't log HEAD requests
            return;
        }
        if ("webengine".equals(reason)) {
            // don't log JSON operation results as downloads
            return;
        }
        EventService eventService = Framework.getService(EventService.class);
        if (eventService == null) {
            return;
        }
        EventContext ctx;
        if (doc != null) {
            CoreSession session = doc.getCoreSession();
            NuxeoPrincipal principal = session == null ? getPrincipal() : session.getPrincipal();
            ctx = new DocumentEventContext(session, principal, doc);
            ctx.setProperty(CoreEventConstants.REPOSITORY_NAME, doc.getRepositoryName());
            ctx.setProperty(CoreEventConstants.IS_PRESIGNED_URL, isPresignedUrl);
        } else {
            ctx = new EventContextImpl(null, getPrincipal());
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

    protected static NuxeoPrincipal getPrincipal() {
        return NuxeoPrincipal.getCurrent();
    }

}
