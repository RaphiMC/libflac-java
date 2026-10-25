/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2001-2009  Josh Coalson
 * Copyright (C) 2011-2023  Xiph.Org Foundation
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Neither the name of the Xiph.org Foundation nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.github.raphimc.libflac;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;

final class FileStats {
    private static final String tempfile_suffix = ".metadata_edit";

    private final BasicFileAttributes basic;
    private final PosixFileAttributes posix;

    private FileStats(BasicFileAttributes basic, PosixFileAttributes posix) {
        this.basic = basic;
        this.posix = posix;
    }

    static Path temporary(Path parent) throws IOException {
        if (parent.getFileSystem().supportedFileAttributeViews().contains("posix"))
            return Files.createTempFile(parent, "flac-metadata-", tempfile_suffix,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-rw-rw-")));
        return Files.createTempFile(parent, "flac-metadata-", tempfile_suffix);
    }

    static void cleanupTempfile(Path tempfilename) {
        if (tempfilename != null)
            try {
                Files.deleteIfExists(tempfilename);
            } catch (IOException ignored) {
            }
    }

    static FileStats read(Path path) throws IOException {
        BasicFileAttributes basic = Files.readAttributes(path, BasicFileAttributes.class);
        PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        return new FileStats(basic, view == null ? null : view.readAttributes());
    }

    void restore(Path path) {

        if (posix != null)
            try {
                Files.setPosixFilePermissions(path, posix.permissions());
            } catch (IOException ignored) {
            }
        try {
            Files.getFileAttributeView(path, BasicFileAttributeView.class)
                    .setTimes(basic.lastModifiedTime(), basic.lastAccessTime(), null);
        } catch (IOException ignored) {
        }
        if (posix != null) {
            try {
                Files.setOwner(path, posix.owner());
            } catch (IOException ignored) {
            }
            try {
                Files.getFileAttributeView(path, PosixFileAttributeView.class).setGroup(posix.group());
            } catch (IOException ignored) {
            }
        }
    }

}
