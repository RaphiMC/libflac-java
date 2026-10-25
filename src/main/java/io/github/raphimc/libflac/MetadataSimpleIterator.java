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

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The level 1 interface provides read-write access to FLAC file metadata and
 * operates directly on the FLAC file.
 *
 * The general usage of this interface is:
 *
 * - Create an iterator using new MetadataSimpleIterator()
 * - Attach it to a file using MetadataSimpleIterator.init() and check the exit code.  Call
 * MetadataSimpleIterator.isWritable() to
 *   see if the file is writable, or only read access is allowed.
 * - Use MetadataSimpleIterator.next() and
 *   MetadataSimpleIterator.prev() to traverse the blocks.
 *   This is does not read the actual blocks themselves.
 *   MetadataSimpleIterator.next() is relatively fast.
 *   MetadataSimpleIterator.prev() is slower since it needs to search
 *   forward from the front of the file.
 * - Use MetadataSimpleIterator.getBlockType() or
 *   MetadataSimpleIterator.getBlock() to access the actual data at
 *   the current iterator position.  The returned object is yours to modify.
 * - Use MetadataSimpleIterator.setBlock() to write a modified block
 *   back.  You must have write permission to the original file.  Make sure to
 *   read the whole comment to MetadataSimpleIterator.setBlock()
 *   below.
 * - Use MetadataSimpleIterator.insertBlockAfter() to add new blocks.
 *
 * - Use MetadataSimpleIterator.deleteBlock() to remove the block
 *   currently referred to by the iterator, or replace it with padding.
 * - Destroy the iterator with MetadataSimpleIterator.close() when
 *   finished.
 *
 * <p>The FLAC file remains open the whole time between
 * MetadataSimpleIterator.init() and
 * MetadataSimpleIterator.close(), so make sure you are not altering
 * the file during this time.
 *
 * <p>Do not modify the {@code isLast} flag or type of returned
 * StreamMetadata objects.  These are managed automatically.
 *
 * <p>If any of the modification functions
 * (MetadataSimpleIterator.setBlock(),
 * MetadataSimpleIterator.deleteBlock(),
 * MetadataSimpleIterator.insertBlockAfter(), etc.) return {@code false},
 * you should close the iterator as it may no longer be valid.
 *
 */
public final class MetadataSimpleIterator implements AutoCloseable {
    private static final int SIMPLE_ITERATOR_MAX_PUSH_DEPTH = 1 + 4;

    private RandomAccessFile file;
    private String filename;
    private FileStats stats;
    private boolean isWritable;
    private MetadataSimpleIteratorStatus status = MetadataSimpleIteratorStatus.OK;
    /** 1 for initial offset, +4 for our own personal use */
    private final long[] offsets = new long[SIMPLE_ITERATOR_MAX_PUSH_DEPTH];
    /** this is the offset to the STREAMINFO block */
    private long firstOffset = -1;
    private int depth;
    private boolean isLast;
    private int type, length;

    /** Create a new iterator instance. */
    public MetadataSimpleIterator() {
        offsets[0] = -1;
    }

    /**
     * Get the current status of the iterator.  Call this after a function
     *  returns {@code false} to get the reason for the error.  Also resets the status
     *  to MetadataSimpleIteratorStatus.OK.
     *
     * @return The current status of the iterator.
     */
    public MetadataSimpleIteratorStatus status() {
        MetadataSimpleIteratorStatus result = status;
        status = MetadataSimpleIteratorStatus.OK;
        return result;
    }

    /**
     * Initialize the iterator to point to the first metadata block in the
     *  given FLAC file.
     *
     * @param filename The path to the FLAC file.
     * @param readOnly If {@code true}, the FLAC file will be opened
     *                             in read-only mode; if {@code false}, the FLAC
     *                             file will be opened for edit even if no
     *                             edits are performed.
     * @param preserveFileStats If {@code true}, the owner and modification
     *                             time will be preserved even if the FLAC
     *                             file is written to.
     * @return {@code false} if an error occurs, else {@code true}.
     */
    public boolean init(String filename, boolean readOnly, boolean preserveFileStats) throws IOException {
        close();
        this.filename = filename;
        if (!readOnly && preserveFileStats)
            try {
                stats = FileStats.read(Path.of(filename));
            } catch (IOException ignored) {

            }
        return primeInput(readOnly);
    }

    /**
     * Returns {@code true} if the FLAC file is writable.  If {@code false}, calls to
     *  MetadataSimpleIterator.setBlock() and
     *  MetadataSimpleIterator.insertBlockAfter() will fail.
     *
     * @return See above.
     */
    public boolean isWritable() {
        requireInitialized();
        return isWritable;
    }

    /**
     * Moves the iterator forward one metadata block, returning {@code false} if
     *  already at the end.
     *
     * @return {@code false} if already at the last metadata block of the chain, else
     *    {@code true}.
     */
    public boolean next() throws IOException {
        requireInitialized();
        if (isLast)
            return false;
        seek(file.getFilePointer() + length);
        offsets[depth] = file.getFilePointer();
        return readHeader();
    }

    /**
     * Moves the iterator backward one metadata block, returning {@code false} if
     *  already at the beginning.
     *
     * @return {@code false} if already at the first metadata block of the chain, else
     *    {@code true}.
     */
    public boolean prev() throws IOException {
        requireInitialized();
        if (offsets[depth] == firstOffset)
            return false;
        seek(firstOffset);
        long thisOffset = firstOffset;
        if (!readHeader())
            return false;
        while (file.getFilePointer() + length < offsets[depth]) {
            seek(file.getFilePointer() + length);
            thisOffset = file.getFilePointer();
            if (!readHeader())
                return false;
        }
        offsets[depth] = thisOffset;
        return true;
    }

    /**
     * Returns a flag telling if the current metadata block is the last.
     *
     * @return {@code true} if the current metadata block is the last in the file,
     *    else {@code false}.
     */

    /* @@@@add to tests */
    public boolean isLast() {
        requireInitialized();
        return isLast;
    }

    /**
     * Get the offset of the metadata block at the current position.  This
     *  avoids reading the actual block data which can save time for large
     *  blocks.
     *
     * @return The offset of the metadata block at the current iterator position.
     *    This is the byte offset relative to the beginning of the file of
     *    the current metadata block's header.
     */

    /* @@@@add to tests */
    public long getBlockOffset() {
        requireInitialized();
        return offsets[depth];
    }

    /**
     * Get the type of the metadata block at the current position.  This
     *  avoids reading the actual block data which can save time for large
     *  blocks.
     *
     * @return The type of the metadata block at the current iterator position.
     */
    public MetadataType getBlockType() {
        requireInitialized();
        return MetadataType.values()[Math.min(type, MetadataType.UNDEFINED.ordinal())];
    }

    /**
     * Get the length of the metadata block at the current position.  This
     *  avoids reading the actual block data which can save time for large
     *  blocks.
     *
     * @return The length of the metadata block at the current iterator position.
     *    The is same length as that in the
     *    <a href="https://xiph.org/flac/format.html#metadata_block_header">metadata block header</a>,
     *    i.e. the length of the metadata body that follows the header.
     */

    /* @@@@add to tests */
    public int getBlockLength() {
        requireInitialized();
        return length;
    }

    /**
     * Get the application ID of the {@code APPLICATION} block at the current
     *  position.  This avoids reading the actual block data which can save
     *  time for large blocks.
     *
     * @return The ID if it was successfully read, else null, in which case you should check status() to find out why.
     */

    /* @@@@add to tests */
    public byte[] getApplicationId() throws IOException {
        requireInitialized();
        if (type != MetadataType.APPLICATION.ordinal()) {
            status = MetadataSimpleIteratorStatus.ILLEGAL_INPUT;
            return null;
        }
        MetadataFileIO.Reader input = reader();
        byte[] id;
        try {
            id = input.read(Format.STREAM_METADATA_APPLICATION_ID_LEN / Byte.SIZE);
        } catch (MetadataFileIO.Failure e) {
            status = e.status;
            return null;
        } catch (IOException e) {
            status = MetadataSimpleIteratorStatus.READ_ERROR;
            throw e;
        }
        /* back up */
        seek(input.position() - Format.STREAM_METADATA_APPLICATION_ID_LEN / Byte.SIZE);
        return id;
    }

    /**
     * Get the metadata block at the current position.  You can modify the
     * block but must use MetadataSimpleIterator.setBlock() to
     * write it back to the FLAC file.
     *
     * @return The current metadata block, or {@code null} if the block could not be read.
     */
    public StreamMetadata getBlock() throws IOException {
        requireInitialized();
        MetadataFileIO.Reader input = reader();
        StreamMetadata block;
        try {
            block = input.readData(new MetadataFileIO.Header(isLast, type, length));
        } catch (MetadataFileIO.Failure e) {
            status = e.status;
            return null;
        } catch (IOException e) {
            status = input.failureStatus();
            throw e;
        }
        status = MetadataSimpleIteratorStatus.OK;
        /* back up to the beginning of the block data to stay consistent */
        seek(offsets[depth] + Format.STREAM_METADATA_HEADER_LENGTH);
        return block;
    }

    /**
     * Write a block back to the FLAC file.  This function tries to be
     *  as efficient as possible; how the block is actually written is
     *  shown by the following:
     *
     *  Existing block is a STREAMINFO block and the new block is a
     *  STREAMINFO block: the new block is written in place.  Make sure
     *  you know what you're doing when changing the values of a
     *  STREAMINFO block.
     *
     *  Existing block is a STREAMINFO block and the new block is a
     *  not a STREAMINFO block: this is an error since the first block
     *  must be a STREAMINFO block.  Returns {@code false} without altering the
     *  file.
     *
     *  Existing block is not a STREAMINFO block and the new block is a
     *  STREAMINFO block: this is an error since there may be only one
     *  STREAMINFO block.  Returns {@code false} without altering the file.
     *
     *  Existing block and new block are the same length: the existing
     *  block will be replaced by the new block, written in place.
     *
     *  Existing block is longer than new block: if usePadding is {@code true},
     *  the existing block will be overwritten in place with the new
     *  block followed by a PADDING block, if possible, to make the total
     *  size the same as the existing block.  Remember that a padding
     *  block requires at least four bytes so if the difference in size
     *  between the new block and existing block is less than that, the
     *  entire file will have to be rewritten, using the new block's
     *  exact size.  If usePadding is {@code false}, the entire file will be
     *  rewritten, replacing the existing block by the new block.
     *
     *  Existing block is shorter than new block: if usePadding is {@code true},
     *  the function will try and expand the new block into the following
     *  PADDING block, if it exists and doing so won't shrink the PADDING
     *  block to less than 4 bytes.  If there is no following PADDING
     *  block, or it will shrink to less than 4 bytes, or usePadding is
     *  {@code false}, the entire file is rewritten, replacing the existing block
     *  with the new block.  Note that in this case any following PADDING
     *  block is preserved as is.
     *
     *  After writing the block, the iterator will remain in the same
     *  place, i.e. pointing to the new block.
     *
     * @param block The block to set.
     * @param usePadding See above.
     * @return {@code true} if successful, else {@code false}.
     */
    public boolean setBlock(StreamMetadata block, boolean usePadding) throws IOException {
        if (!checkWrite())
            return false;
        if ((type == MetadataType.STREAMINFO.ordinal() || block.typeCode == MetadataType.STREAMINFO.ordinal())
                && type != block.typeCode)
            return fail(MetadataSimpleIteratorStatus.ILLEGAL_INPUT);
        block.isLast = isLast;
        int newLength = block.getLength();
        if (length == newLength)
            return writeMetadataBlockStationary(block);
        if (length > newLength) {
            if (usePadding && length >= Format.STREAM_METADATA_HEADER_LENGTH + newLength)
                return writeMetadataBlockStationaryWithPadding(block,
                        length - Format.STREAM_METADATA_HEADER_LENGTH - newLength, block.isLast);
            return rewriteWholeFile(block, false);
        }
        /* length < newLength */
        int paddingLeftover = 0;
        boolean paddingIsLast = false;
        if (usePadding) {
            /* first see if we can even use padding */
            if (isLast)
                usePadding = false;
            else {
                int required = newLength - length;
                push();
                if (!next()) {
                    pop();
                    return false;
                }
                if (type != MetadataType.PADDING.ordinal())
                    usePadding = false;
                else if (Format.STREAM_METADATA_HEADER_LENGTH + length == required)
                    block.isLast = isLast;
                else if (length < required)
                    usePadding = false;
                else {
                    paddingLeftover = Format.STREAM_METADATA_HEADER_LENGTH + length - required;
                    paddingIsLast = isLast;
                    block.isLast = false;
                }
                if (!pop())
                    return false;
            }
        }
        if (!usePadding)
            return rewriteWholeFile(block, false);
        return paddingLeftover == 0 ? writeMetadataBlockStationary(block) : writeMetadataBlockStationaryWithPadding(
                block, paddingLeftover - Format.STREAM_METADATA_HEADER_LENGTH, paddingIsLast);
    }

    /**
     * This is similar to MetadataSimpleIterator.setBlock()
     *  except that instead of writing over an existing block, it appends
     *  a block after the existing block.  {@code usePadding} is again used to
     *  tell the function to try an expand into following padding in an
     *  attempt to avoid rewriting the entire file.
     *
     *  This function will fail and return {@code false} if given a STREAMINFO
     *  block.
     *
     *  After writing the block, the iterator will be pointing to the
     *  new block.
     *
     * @param block The block to set.
     * @param usePadding See above.
     * @return {@code true} if successful, else {@code false}.
     */
    public boolean insertBlockAfter(StreamMetadata block, boolean usePadding) throws IOException {
        if (!checkWrite())
            return false;
        if (block.typeCode == MetadataType.STREAMINFO.ordinal())
            return fail(MetadataSimpleIteratorStatus.ILLEGAL_INPUT);
        block.isLast = isLast;
        int paddingLeftover = 0;
        boolean paddingIsLast = false;
        if (usePadding) {
            /* first see if we can even use padding */
            if (isLast)
                usePadding = false;
            else {
                push();
                if (!next()) {
                    pop();
                    return false;
                }
                if (type != MetadataType.PADDING.ordinal())
                    usePadding = false;
                else if (length == block.getLength())
                    block.isLast = isLast;
                else if (length < Format.STREAM_METADATA_HEADER_LENGTH + block.getLength())
                    usePadding = false;
                else {
                    paddingLeftover = length - block.getLength();
                    paddingIsLast = isLast;
                    block.isLast = false;
                }
                if (!pop())
                    return false;
            }
        }
        if (!usePadding)
            return rewriteWholeFile(block, true);
        /* move to the next block, which is suitable padding */
        if (!next())
            return false;
        return paddingLeftover == 0 ? writeMetadataBlockStationary(block) : writeMetadataBlockStationaryWithPadding(
                block, paddingLeftover - Format.STREAM_METADATA_HEADER_LENGTH, paddingIsLast);
    }

    /**
     * Deletes the block at the current position.  This will cause the
     *  entire FLAC file to be rewritten, unless {@code usePadding} is {@code true},
     *  in which case the block will be replaced by an equal-sized PADDING
     *  block.  The iterator will be left pointing to the block before the
     *  one just deleted.
     *
     *  You may not delete the STREAMINFO block.
     *
     * @param usePadding See above.
     * @return {@code true} if successful, else {@code false}.
     */
    public boolean deleteBlock(boolean usePadding) throws IOException {
        if (!checkWrite())
            return false;
        if (type == MetadataType.STREAMINFO.ordinal())
            return fail(MetadataSimpleIteratorStatus.ILLEGAL_INPUT);
        if (usePadding) {
            if (!setBlock(new Padding(length), false))
                return false;
            return prev();
        }
        return rewriteWholeFile(null, false);
    }

    /** Close an iterator instance. */
    @Override
    public void close() throws IOException {
        try {
            if (file != null)
                file.close();
        } finally {
            if (file != null && stats != null)
                stats.restore(Path.of(filename));
            file = null;
            filename = null;
            stats = null;
            isWritable = false;
        }
    }

    private boolean primeInput(boolean readOnly) throws IOException {
        isWritable = false;
        try {

            if (!readOnly && !Files.exists(Path.of(filename)))
                throw new FileNotFoundException(filename);
            if (!readOnly) {
                try {
                    file = new RandomAccessFile(filename, "rw");
                    isWritable = true;
                } catch (FileNotFoundException e) {
                    if (!Files.isRegularFile(Path.of(filename)) || !Files.isReadable(Path.of(filename)))
                        throw e;
                }
            }
            if (file == null)
                file = new RandomAccessFile(filename, "r");
        } catch (IOException e) {
            status = MetadataSimpleIteratorStatus.ERROR_OPENING_FILE;
            throw e;
        }
        MetadataFileIO.Reader input = reader();
        try {
            if (!input.seekToFirstMetadataBlock())
                return fail(MetadataSimpleIteratorStatus.NOT_A_FLAC_FILE);
        } catch (MetadataFileIO.Failure e) {
            return fail(e.status);
        } catch (IOException e) {
            status = input.failureStatus();
            throw e;
        }
        depth = 0;
        firstOffset = offsets[0] = input.position();
        boolean ok = readHeader();
        /*
         * The first metadata block must be a streaminfo. If this is not the
         * case, the file is invalid and assumptions made elsewhere in the
         * code are invalid
         */
        if (type != MetadataType.STREAMINFO.ordinal())
            return fail(MetadataSimpleIteratorStatus.BAD_METADATA);
        return ok;
    }

    private void requireInitialized() {
        if (file == null)
            throw new IllegalStateException("iterator has not been initialized");
    }

    private boolean checkWrite() {
        requireInitialized();
        return isWritable || fail(MetadataSimpleIteratorStatus.NOT_WRITABLE);
    }

    private boolean fail(MetadataSimpleIteratorStatus failure) {
        status = failure;
        return false;
    }

    private MetadataFileIO.Reader reader() throws IOException {
        return new MetadataFileIO.Reader(new MetadataFileIO.FileCallbacks(file), file.getFilePointer());
    }

    private boolean readHeader() throws IOException {
        try {
            MetadataFileIO.Header header = reader().readHeader();
            isLast = header.isLast();
            type = header.type();
            length = header.length();
            return true;
        } catch (MetadataFileIO.Failure e) {
            return fail(e.status);
        } catch (IOException e) {
            status = MetadataSimpleIteratorStatus.READ_ERROR;
            throw e;
        }
    }

    private void writeHeader(RandomAccessFile file, StreamMetadata block) throws IOException {
        try {
            MetadataFileIO.writeHeader(new MetadataFileIO.FileCallbacks(file), block);
        } catch (IOException e) {
            status = MetadataSimpleIteratorStatus.WRITE_ERROR;
            throw e;
        }
    }

    private void writeData(RandomAccessFile file, StreamMetadata block) throws IOException {
        try {
            MetadataFileIO.writeData(new MetadataFileIO.FileCallbacks(file), block);
            status = MetadataSimpleIteratorStatus.OK;
        } catch (IOException e) {
            status = MetadataSimpleIteratorStatus.WRITE_ERROR;
            throw e;
        }
    }

    private boolean writeMetadataBlockStationary(StreamMetadata block) throws IOException {
        seek(offsets[depth]);
        writeHeader(file, block);
        writeData(file, block);
        seek(offsets[depth]);
        return readHeader();
    }

    private boolean writeMetadataBlockStationaryWithPadding(StreamMetadata block, int paddingLength,
            boolean paddingIsLast) throws IOException {
        seek(offsets[depth]);
        block.isLast = false;
        writeHeader(file, block);
        writeData(file, block);
        Padding padding = new Padding(paddingLength);
        padding.isLast = paddingIsLast;
        writeHeader(file, padding);
        writeData(file, padding);
        seek(offsets[depth]);
        return readHeader();
    }

    private boolean rewriteWholeFile(StreamMetadata block, boolean append) throws IOException {
        long savedOffset = offsets[depth], fixupOffset = -1;
        /* 0 => no need to change any isLast flags */
        int savedLength = length, fixupCode = 0;
        if (isLast) {
            if (append) {
                /* 1 => clear the isLast flag at the following offset */
                fixupCode = 1;
                fixupOffset = savedOffset;
            } else if (block == null) {
                push();
                if (!prev()) {
                    pop();
                    return false;
                }
                /* -1 => set the isLast the flag at the following offset */
                fixupCode = -1;
                fixupOffset = offsets[depth];
                if (!pop())
                    return false;
            }
        }
        seek(0);
        Path original = Path.of(filename), temp;
        try {
            temp = FileStats.temporary(original.toAbsolutePath().getParent());
        } catch (IOException e) {
            status = MetadataSimpleIteratorStatus.ERROR_OPENING_FILE;
            throw e;
        }
        boolean moved = false, wasWritable = isWritable;
        try {
            RandomAccessFile output;
            try {
                output = new RandomAccessFile(temp.toFile(), "rw");
            } catch (IOException e) {
                status = MetadataSimpleIteratorStatus.ERROR_OPENING_FILE;
                throw e;
            }
            try (output) {
                copyNBytesFromFile(file, output,
                        append ? savedOffset + Format.STREAM_METADATA_HEADER_LENGTH + savedLength : savedOffset);
                if (block != null) {
                    writeHeader(output, block);
                    writeData(output, block);
                }
                seek(savedOffset + Format.STREAM_METADATA_HEADER_LENGTH + savedLength);
                copyRemainingBytesFromFile(file, output);
                /*
                 * if code == 1, it means a block was appended to the end so
                 *   we have to clear the isLast flag of the previous block
                 * if code == -1, it means the last block was deleted so
                 *   we have to set the isLast flag of the previous block
                 */
                if (fixupCode != 0) {
                    byte[] buffer = new byte[1];
                    seekTemporary(output, fixupOffset);
                    if (readChunk(output, buffer, 1) != 1) {
                        status = MetadataSimpleIteratorStatus.READ_ERROR;
                        throw new EOFException("missing metadata last-block flag");
                    }
                    seekTemporary(output, fixupOffset);
                    /* MAGIC NUMBERs here; we know the isLast flag is the high bit of the byte at this location */
                    buffer[0] = (byte) (fixupCode > 0 ? buffer[0] & 127 : buffer[0] | 128);
                    writeChunk(output, buffer, 1);
                }
            }
            file.close();
            file = null;
            try {
                Files.move(temp, original, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                status = MetadataSimpleIteratorStatus.RENAME_ERROR;
                throw e;
            }
            moved = true;
            if (stats != null)
                stats.restore(original);
            if (!primeInput(!wasWritable))
                return false;
            if (block == null) {
                while (offsets[depth] + Format.STREAM_METADATA_HEADER_LENGTH + length < savedOffset)
                    if (!next())
                        return false;
            } else {
                /* move the iterator to it's original block faster by faking a push, then doing a pop() */
                offsets[0] = savedOffset;
                depth++;
                if (!pop())
                    return false;
            }
            return !append || next();
        } catch (IOException e) {
            if (status == MetadataSimpleIteratorStatus.OK)
                status = MetadataSimpleIteratorStatus.WRITE_ERROR;
            throw e;
        } finally {
            if (!moved)
                FileStats.cleanupTempfile(temp);
        }
    }

    private void push() {
        offsets[depth + 1] = offsets[depth];
        depth++;
    }

    private boolean pop() throws IOException {
        depth--;
        seek(offsets[depth]);
        return readHeader();
    }

    private void seek(long offset) throws IOException {
        try {
            file.seek(offset);
        } catch (IOException e) {
            status = MetadataSimpleIteratorStatus.SEEK_ERROR;
            throw e;
        }
    }

    private void seekTemporary(RandomAccessFile output, long offset) throws IOException {
        try {
            output.seek(offset);
        } catch (IOException e) {
            status = MetadataSimpleIteratorStatus.SEEK_ERROR;
            throw e;
        }
    }

    private int readChunk(RandomAccessFile input, byte[] buffer, int length) throws IOException {
        try {
            return input.read(buffer, 0, length);
        } catch (IOException e) {
            status = MetadataSimpleIteratorStatus.READ_ERROR;
            throw e;
        }
    }

    private void writeChunk(RandomAccessFile output, byte[] buffer, int length) throws IOException {
        try {
            output.write(buffer, 0, length);
        } catch (IOException e) {
            status = MetadataSimpleIteratorStatus.WRITE_ERROR;
            throw e;
        }
    }

    private void copyNBytesFromFile(RandomAccessFile file, RandomAccessFile tempfile, long bytes) throws IOException {
        byte[] buffer = new byte[8192];
        while (bytes > 0) {
            int n = (int) Math.min(bytes, buffer.length);
            if (readChunk(file, buffer, n) != n) {
                status = MetadataSimpleIteratorStatus.READ_ERROR;
                throw new EOFException("truncated metadata file");
            }
            writeChunk(tempfile, buffer, n);
            bytes -= n;
        }
    }

    private void copyRemainingBytesFromFile(RandomAccessFile file, RandomAccessFile tempfile) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = readChunk(file, buffer, buffer.length)) > 0)
            writeChunk(tempfile, buffer, n);
    }
}
