/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.trackers.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.io.FileCleaningTracker;
import org.apache.commons.io.FileDeleteStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.common.xmap.registry.XRegistry;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.model.DefaultComponent;
import com.hyland.openarch.runtime.services.event.EventService;
import com.hyland.openarch.runtime.trackers.concurrent.ThreadEventHandler;
import com.hyland.openarch.runtime.trackers.concurrent.ThreadEventListener;

/**
 * Files event tracker which delete files once the runtime leave the threads or at least once the associated marker
 * object is garbaged.
 *
 * @author Stephane Lacoin at Nuxeo (aka matic)
 * @since 6.0
 * @see ThreadEventHandler
 */
public class FileEventTracker extends DefaultComponent {

    protected static final Logger log = LogManager.getLogger(FileEventTracker.class);

    protected static final String XP = "configs";

    protected static SafeFileDeleteStrategy deleteStrategy = new SafeFileDeleteStrategy();

    static class SafeFileDeleteStrategy extends FileDeleteStrategy {

        protected CopyOnWriteArrayList<String> protectedPaths = new CopyOnWriteArrayList<>();

        protected SafeFileDeleteStrategy() {
            super("DoNotTouchNuxeoBinaries");
        }

        protected void registerProtectedPath(String path) {
            protectedPaths.add(path);
        }

        protected boolean isFileProtected(File fileToDelete) {
            for (String path : protectedPaths) {
                // do not delete files under the protected directories
                if (fileToDelete.getPath().startsWith(path)) {
                    log.warn("Protect file {} from deletion : check usage of Framework.trackFile", fileToDelete.getPath());
                    return true;
                }
            }
            return false;
        }

        @Override
        protected boolean doDelete(File fileToDelete) throws IOException {
            if (!isFileProtected(fileToDelete)) {
                return super.doDelete(fileToDelete);
            } else {
                return false;
            }
        }

    }

    /**
     * Registers a protected path under which files should not be deleted
     *
     * @since 7.2
     */
    public static void registerProtectedPath(String path) {
        deleteStrategy.registerProtectedPath(path);
    }

    protected static class GCDelegate implements FileEventHandler {
        protected FileCleaningTracker delegate = new FileCleaningTracker();

        @Override
        public void onFile(File file, Object marker) {
            delegate.track(file, marker, deleteStrategy);
        }
    }

    protected class ThreadDelegate implements FileEventHandler {

        protected final boolean isLongRunning;

        protected final Thread owner = Thread.currentThread();

        protected final Set<File> files = new HashSet<>();

        protected ThreadDelegate(boolean isLongRunning) {
            this.isLongRunning = isLongRunning;
        }

        @Override
        public void onFile(File file, Object marker) {
            if (!owner.equals(Thread.currentThread())) {
                return;
            }
            if (isLongRunning) {
                gc.onFile(file, marker);
            }
            files.add(file);
        }

    }

    @XObject("enableThreadsTracking")
    @XRegistry
    public static class EnableThreadsTracking {

    }

    protected final GCDelegate gc = new GCDelegate();

    protected final ThreadLocal<ThreadDelegate> threads = new ThreadLocal<>();

    protected final ThreadEventListener threadsListener = new ThreadEventListener(new ThreadEventHandler() {

        @Override
        public void onEnter(boolean isLongRunning) {
            setThreadDelegate(isLongRunning);
        }

        @Override
        public void onLeave() {
            resetThreadDelegate();
        }

    });

    protected final FileEventListener filesListener = new FileEventListener(
            (file, marker) -> onContext().onFile(file, marker));

    @Override
    public void activate() {
        filesListener.install();
        setThreadDelegate(false);
    }

    @Override
    public int getApplicationStartedOrder() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void start() {
        resetThreadDelegate();
        if (getRegistryContribution(XP).isPresent()) {
            threadsListener.install();
        }
    }

    @Override
    public void deactivate() {
        if (Framework.getService(EventService.class) != null) {
            if (threadsListener.isInstalled()) {
                threadsListener.uninstall();
            }
            filesListener.uninstall();
        }
    }

    protected FileEventHandler onContext() {
        FileEventHandler actual = threads.get();
        if (actual == null) {
            actual = gc;
        }
        return actual;
    }

    protected void setThreadDelegate(boolean isLongRunning) {
        if (threads.get() != null) {
            throw new IllegalStateException("Thread delegate already installed");
        }
        threads.set(new ThreadDelegate(isLongRunning));
    }

    protected void resetThreadDelegate() throws IllegalStateException {
        ThreadDelegate actual = threads.get();
        if (actual == null) {
            return;
        }
        try {
            for (File file : actual.files) {
                if (!deleteStrategy.isFileProtected(file)) {
                    try {
                        Files.delete(file.toPath());
                    } catch (IOException e){
                        log.error(e, e);
                    }
                }
            }
        } finally {
            threads.remove();
        }
    }

}
