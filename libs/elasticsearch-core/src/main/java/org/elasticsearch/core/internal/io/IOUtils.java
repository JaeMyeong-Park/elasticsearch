/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elasticsearch.core.internal.io;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utilities for common I/O methods. Borrowed heavily from Lucene (org.apache.lucene.util.IOUtils).
 */
public final class IOUtils {

    private IOUtils() {

    }

    /**
     * Closes all given <tt>Closeable</tt>s. Some of the <tt>Closeable</tt>s may be null; they are ignored. After everything is closed, the
     * method either throws the first exception it hit while closing, or completes normally if there were no exceptions.
     *
     * @param objects objects to close
     */
    public static void close(final Closeable... objects) throws IOException {
        close(Arrays.asList(objects));
    }

    /**
     * Closes all given {@link Closeable}s.
     *
     * @param objects objects to close
     *
     * @see #close(Closeable...)
     */
    public static void close(final Iterable<? extends Closeable> objects) throws IOException {
        Throwable th = null;

        for (final Closeable object : objects) {
            try {
                if (object != null) {
                    object.close();
                }
            } catch (final Throwable t) {
                addSuppressed(th, t);
                if (th == null) {
                    th = t;
                }
            }
        }

        if (th != null) {
            throw rethrowAlways(th);
        }
    }

    /**
     * Closes all given {@link Closeable}s, suppressing all thrown exceptions. Some of the {@link Closeable}s may be null, they are ignored.
     *
     * @param objects objects to close
     */
    public static void closeWhileHandlingException(final Closeable... objects) {
        closeWhileHandlingException(Arrays.asList(objects));
    }

    /**
     * Closes all given {@link Closeable}s, suppressing all thrown exceptions.
     *
     * @param objects objects to close
     *
     * @see #closeWhileHandlingException(Closeable...)
     */
    public static void closeWhileHandlingException(final Iterable<? extends Closeable> objects) {
        for (final Closeable object : objects) {
            // noinspection EmptyCatchBlock
            try {
                if (object != null) {
                    object.close();
                }
            } catch (final Throwable t) {

            }
        }
    }

    /**
     * Adds a {@link Throwable} to the list of suppressed {@link Exception}s of the first {@link Throwable}.
     *
     * @param exception  the exception to add a suppression to, if non-null
     * @param suppressed the exception to suppress
     */
    private static void addSuppressed(final Throwable exception, final Throwable suppressed) {
        if (exception != null && suppressed != null) {
            exception.addSuppressed(suppressed);
        }
    }

    /**
     * This utility method takes a previously caught (non-null) {@link Throwable} and rethrows either the original argument if it was a
     * subclass of the {@link IOException} or an {@link RuntimeException} with the cause set to the argument.
     * <p>
     * This method <strong>never returns any value</strong>, even though it declares a return value of type {@link Error}. The return
     * value declaration is very useful to let the compiler know that the code path following the invocation of this method is unreachable.
     * So in most cases the invocation of this method will be guarded by an {@code if} and used together with a {@code throw} statement, as
     * in:
     * <p>
     * <pre>{@code
     *   if (t != null) throw IOUtils.rethrowAlways(t)
     * }
     * </pre>
     *
     * @param th the throwable to rethrow; <strong>must not be null</strong>
     * @return this method always results in an exception, it never returns any value; see method documentation for details and usage
     * example
     * @throws IOException      if the argument was an instance of {@link IOException}
     * @throws RuntimeException with the {@link RuntimeException#getCause()} set to the argument, if it was not an instance of
     *                          {@link IOException}
     */
    private static Error rethrowAlways(final Throwable th) throws IOException, RuntimeException {
        if (th == null) {
            throw new AssertionError("rethrow argument must not be null.");
        }

        if (th instanceof IOException) {
            throw (IOException) th;
        }

        if (th instanceof RuntimeException) {
            throw (RuntimeException) th;
        }

        if (th instanceof Error) {
            throw (Error) th;
        }

        throw new RuntimeException(th);
    }

    /**
     * Deletes all given files, suppressing all thrown {@link IOException}s. Some of the files may be null, if so they are ignored.
     *
     * @param files the paths of files to delete
     */
    public static void deleteFilesIgnoringExceptions(final Path... files) {
        deleteFilesIgnoringExceptions(Arrays.asList(files));
    }

    /**
     * Deletes all given files, suppressing all thrown {@link IOException}s. Some of the files may be null, if so they are ignored.
     *
     * @param files the paths of files to delete
     */
    public static void deleteFilesIgnoringExceptions(final Collection<? extends Path> files) {
        for (final Path name : files) {
            if (name != null) {
                // noinspection EmptyCatchBlock
                try {
                    Files.delete(name);
                } catch (final Throwable ignored) {

                }
            }
        }
    }

    /**
     * Deletes one or more files or directories (and everything underneath it).
     *
     * @throws IOException if any of the given files (or their sub-hierarchy files in case of directories) cannot be removed.
     */
    public static void rm(final Path... locations) throws IOException {
        final LinkedHashMap<Path,Throwable> unremoved = rm(new LinkedHashMap<>(), locations);
        if (!unremoved.isEmpty()) {
            final StringBuilder b = new StringBuilder("could not remove the following files (in the order of attempts):\n");
            for (final Map.Entry<Path,Throwable> kv : unremoved.entrySet()) {
                b.append("   ")
                        .append(kv.getKey().toAbsolutePath())
                        .append(": ")
                        .append(kv.getValue())
                        .append("\n");
            }
            throw new IOException(b.toString());
        }
    }

    private static LinkedHashMap<Path,Throwable> rm(final LinkedHashMap<Path,Throwable> unremoved, final Path... locations) {
        if (locations != null) {
            for (final Path location : locations) {
                // TODO: remove this leniency
                if (location != null && Files.exists(location)) {
                    try {
                        Files.walkFileTree(location, new FileVisitor<Path>() {
                            @Override
                            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(final Path dir, final IOException impossible) throws IOException {
                                assert impossible == null;

                                try {
                                    Files.delete(dir);
                                } catch (final IOException e) {
                                    unremoved.put(dir, e);
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                                try {
                                    Files.delete(file);
                                } catch (final IOException exc) {
                                    unremoved.put(file, exc);
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
                                if (exc != null) {
                                    unremoved.put(file, exc);
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } catch (final IOException impossible) {
                        throw new AssertionError("visitor threw exception", impossible);
                    }
                }
            }
        }
        return unremoved;
    }

    // TODO: replace with constants class if needed (cf. org.apache.lucene.util.Constants)
    private static final boolean LINUX = System.getProperty("os.name").startsWith("Linux");
    private static final boolean MAC_OS_X = System.getProperty("os.name").startsWith("Mac OS X");

    /**
     * Ensure that any writes to the given file is written to the storage device that contains it. The {@code isDir} parameter specifies
     * whether or not the path to sync is a directory. This is needed because we open for read and ignore an {@link IOException} since not
     * all filesystems and operating systems support fsyncing on a directory. For regular files we must open for write for the fsync to have
     * an effect.
     *
     * @param fileToSync the file to fsync
     * @param isDir      if true, the given file is a directory (we open for read and ignore {@link IOException}s, because not all file
     *                   systems and operating systems allow to fsync on a directory)
     */
    public static void fsync(final Path fileToSync, final boolean isDir) throws IOException {
        try (FileChannel file = FileChannel.open(fileToSync, isDir ? StandardOpenOption.READ : StandardOpenOption.WRITE)) {
            file.force(true);
        } catch (final IOException ioe) {
            if (isDir) {
                assert (LINUX || MAC_OS_X) == false :
                        "on Linux and MacOSX fsyncing a directory should not throw IOException, "+
                                "we just don't want to rely on that in production (undocumented); got: " + ioe;
                // ignore exception if it is a directory
                return;
            }
            // throw original exception
            throw ioe;
        }
    }

}
