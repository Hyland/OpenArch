/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.document.test.multipart;

import jakarta.servlet.http.Part;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

public class MockMultiPartFilePart implements Part {

    private MockMultipartFile mockMultipartFile;

    private File tempFile;

    public MockMultiPartFilePart(MockMultipartFile mockMultipartFile) {
        this.mockMultipartFile = mockMultipartFile;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return mockMultipartFile.getInputStream();
    }

    @Override
    public String getContentType() {
        return mockMultipartFile.getContentType();
    }

    @Override
    public String getName() {
        return mockMultipartFile.getName();
    }

    @Override
    public String getSubmittedFileName() {
        return mockMultipartFile.getOriginalFilename();
    }

    @Override
    public long getSize() {
        return mockMultipartFile.getSize();
    }

    @Override
    public void write(String fileName) throws IOException {
        String[] fileNameParts = mockMultipartFile.getOriginalFilename().split("\\.");
        tempFile = File.createTempFile(fileNameParts[0], "." + fileNameParts[1]);
        Files.copy(mockMultipartFile.getInputStream(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void delete() throws IOException {
        if(tempFile!=null) {
            tempFile.delete();
        }
    }

    @Override
    public String getHeader(String name) {
        if (HttpHeaders.CONTENT_TYPE.equals(name)) {
            return mockMultipartFile.getContentType();
        }
        return "";
    }

    @Override
    public Collection<String> getHeaders(String name) {
        return List.of(getHeader(name));
    }

    @Override
    public Collection<String> getHeaderNames() {
        return List.of(HttpHeaders.CONTENT_TYPE);
    }
}
