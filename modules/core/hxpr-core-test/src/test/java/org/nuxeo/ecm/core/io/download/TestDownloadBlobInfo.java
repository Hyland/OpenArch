package org.nuxeo.ecm.core.io.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
public class TestDownloadBlobInfo {

    @Test
    public void testParseDownloadPath() {
        assertParsed(null, null, "");
        assertParsed(null, null, "/");
        assertParsed("foo", null, "/foo");
        assertParsed("foo", null, "/foo/");
        assertParsed("foo", "bar", "/foo/bar");
        assertParsed("foo/bar", "baz", "/foo/bar/baz");
        assertParsed("foo/bar/baz", "moo", "/foo/bar/baz/moo");
        assertParsed("sysfile_blob", null, "/sysfile_blob");
        assertParsed("sysfile_blob", "file.txt", "/sysfile_blob/file.txt");
        assertParsed("sysfiles_blobs/0/blob", null, "/sysfiles_blobs/0/blob");
        assertParsed("sysfiles_blobs/0/blob", "image.png", "/sysfiles_blobs/0/blob/image.png");
        assertParsed("foo/bar", "baz", "/foo/bar/baz");
    }

    protected void assertParsed(String xpath, String filename, String string) {
        DownloadBlobInfo downloadBlobInfo = new DownloadBlobInfo("somerepo/someid" + string);
        assertEquals("somerepo", downloadBlobInfo.repository);
        assertEquals("someid", downloadBlobInfo.docId);
        assertEquals(xpath, downloadBlobInfo.xpath);
        assertEquals(filename, downloadBlobInfo.filename);
    }

    @Test
    public void cannotConstructDownloadBlobInfo() {
        try {
            new DownloadBlobInfo("foo");
            fail();
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

}
