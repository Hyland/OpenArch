package org.nuxeo.osgi;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;

class JarBundleFileTest {

    @Test
    void extractNestedJarSuccessfully() {
        File tempJar = null;
        File tempDir = null;
        try {
            tempJar = File.createTempFile("test", ".jar");
            tempJar.deleteOnExit();
            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempJar))) {
                jos.putNextEntry(new ZipEntry("nested.jar"));
                jos.write("dummy".getBytes());
                jos.closeEntry();
            }

            try (JarFile jarFile = new JarFile(tempJar)) {
                ZipEntry entry = jarFile.getEntry("nested.jar");
                assertNotNull(entry, "Entry should exist in test jar");

                tempDir = Files.createTempDirectory("jartest").toFile();
                tempDir.deleteOnExit();
                File dest = new File(tempDir, "nested.jar");
                dest.deleteOnExit();

                JarBundleFile.extractNestedJar(jarFile, entry, dest);

                assertTrue(dest.exists(), "Extracted jar should exist");
            }
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        } finally {
            if (tempJar != null && tempJar.exists()) tempJar.delete();
            if (tempDir != null && tempDir.exists()) tempDir.delete();
        }
    }

    @Test
    void extractNestedJarShouldThrowExceptionOnDirectoryTraversalAttack() {
        File tempJar = null;
        File tempDir = null;
        try {
            tempJar = File.createTempFile("test", ".jar");
            tempJar.deleteOnExit();
            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempJar))) {
                jos.putNextEntry(new ZipEntry("../evil.jar"));
                jos.write("dummy".getBytes());
                jos.closeEntry();
            }

            try (JarFile jarFile = new JarFile(tempJar)) {
                ZipEntry entry = jarFile.getEntry("../evil.jar");
                assertNotNull(entry, "Entry should exist in test jar");

                tempDir = Files.createTempDirectory("jartest").toFile();
                tempDir.deleteOnExit();
                File dest = new File(tempDir, "/../../etc/evil.jar");

                try {
                    JarBundleFile.extractNestedJar(jarFile, entry, dest);
                    fail("Expected IOException for directory traversal attack was not thrown");
                } catch (IOException e) {
                    assertTrue(e.getMessage().startsWith("Invalid destination path for nested jar"), "Should throw IOException for directory traversal attack");
                }
            }
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        } finally {
            if (tempJar != null && tempJar.exists()) tempJar.delete();
            if (tempDir != null && tempDir.exists()) tempDir.delete();
        }
    }
}
