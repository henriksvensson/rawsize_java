/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
  Compile: javac -classpath '.:./commons-io-2.4/commons-io-2.4.jar' *.java
  Run:     java  -classpath '.:./commons-io-2.4/commons-io-2.4.jar' RawSize /tmp/orig/ /tmp/copy/
*/

import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;
import org.apache.commons.io.FilenameUtils;

import java.util.concurrent.*;

/**
 * Example to watch a directory (or tree) for changes to files.
 */

public class WatchDir implements Runnable {

    private final WatchService watcher;
    private final Map<WatchKey,Path> keys;
    private RFileFactory fact;
    private ExecutorService executor;

    private static Logger logger = Logger.getGlobal();

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Creates a WatchService and registers the given directory
     */
    WatchDir(Path dirOrig, Path dirCopy, ExecutorService executor) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,Path>();
        this.executor = executor;
        this.fact = new RFileFactory(dirOrig, dirCopy);

        logger.info("Scanning original path: " + dirOrig);
        registerAll(dirOrig);

        logger.info("Scanning copy path:     " + dirCopy);
        registerAll(dirCopy);
    }

    /**
     * Process all events for keys queued to the watcher
     */
    @Override
    public void run() {
        logger.info("Starting watch service.");
        for (;;) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                logger.warning("WatchKey not found in registered keys.");
                continue;
            }

            for (WatchEvent<?> event: key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                // TBD - provide example of how OVERFLOW event is handled
                if (kind == OVERFLOW) {
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path fullPath = dir.resolve(name);
                RFile rFile = fact.getRFile(fullPath);

                // Log event
                logger.info(event.kind().name() + ": " + fullPath);

                // Handle the case where an event has occurred in the original
                // directory path. New and changed files should be (re)converted,
                // deleted files should be removed in the copy path.
                if(rFile.isOrig()) {
                    
                    if(kind == ENTRY_CREATE || kind == ENTRY_MODIFY) {
                        // Update the image from the original
                        executor.execute(rFile);
                    }
                }

                // Handle the case where a file has been removed in either
                // the original or the copy directory.
                if(kind == ENTRY_DELETE) {
                    rFile.delete();
                }

                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                if (kind == ENTRY_CREATE) {
                    try {
                        if (Files.isDirectory(fullPath, NOFOLLOW_LINKS)) {
                            // Follow changes in the new directory.
                            registerAll(fullPath);
                        }
                    } catch (IOException x) {
                        // ignore to keep sample readable
                    }
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

}
