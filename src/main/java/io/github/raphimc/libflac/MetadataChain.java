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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The level 2 interface provides read-write access to FLAC file metadata;
 * all metadata is read into memory, operated on in memory, and then written
 * to file, which is more efficient than level 1 when editing multiple blocks.
 *
 * Currently Ogg FLAC is supported for read only, via
 * MetadataChain.readOgg() but a subsequent
 * MetadataChain.write() will fail.
 *
 * The general usage of this interface is:
 *
 * - Create a new chain using new MetadataChain().  A chain is a
 *   linked list of FLAC metadata blocks.
 * - Read all metadata into the chain from a FLAC file using
 *   MetadataChain.read() or MetadataChain.readOgg() and
 *   check the status.
 * - Optionally, consolidate the padding using
 *   MetadataChain.mergePadding() or
 *   MetadataChain.sortPadding().
 * - Create a new iterator using new MetadataIterator()
 * - Initialize the iterator to point to the first element in the chain
 *   using MetadataIterator.init()
 * - Traverse the chain using MetadataIterator.next and
 *   MetadataIterator.prev().
 * - Get a block for reading or modification using
 *   MetadataIterator.getBlock().  The object reference
 *   inside the chain is returned, so the block is yours to modify.
 *   Changes will be reflected in the FLAC file when you write the
 *   chain.  You can also add and delete blocks (see functions below).
 * - When done, write out the chain using MetadataChain.write().
 *   Make sure to read the whole comment to the function below.
 *
 * <p>Even though the FLAC file is not open while the chain is being
 * manipulated, you must not alter the file externally during
 * this time.  The chain assumes the FLAC file will not change
 * between the time of MetadataChain.read()/MetadataChain.readOgg()
 * and MetadataChain.write().
 *
 * <p>Do not modify the isLast flag or type of returned
 * StreamMetadata objects.  These are managed automatically.
 *
 * In the same way, blocks passed to MetadataIterator.setBlock()
 * become owned by the chain.
 *
 */
public final class MetadataChain {

    static final class Node {
        StreamMetadata data;
        Node prev, next;
    }

    /** will be null if using callbacks */
    private String filename;
    private boolean isOgg;
    Node head, tail;
    int nodes;
    private MetadataChainStatus status = MetadataChainStatus.OK;
    private long firstOffset, lastOffset;
    /**
     * This is the length of the chain initially read from the FLAC file.
     * it is used to compare against the current length to decide whether
     * or not the whole file has to be rewritten.
     */
    private long initialLength;

    /**
     * Get the current status of the chain.  Call this after a function
     *  returns {@code false} to get the reason for the error.  Also resets the
     *  status to MetadataChainStatus.OK.
     *
     * @return The current status of the chain.
     */
    public MetadataChainStatus status() {
        MetadataChainStatus s = status;
        status = MetadataChainStatus.OK;
        return s;
    }

    /**
     * Read all metadata from a FLAC file into the chain.
     *
     * @param filename The path to the FLAC file to read.
     * @return {@code true} if a valid list of metadata blocks was read from
     *    {@code filename}, else {@code false}.  On failure, check the status with
     *    MetadataChain.status().
     */
    public boolean read(String filename) throws IOException {
        return readInternal(filename, false);
    }

    /**
     * Read all metadata from an Ogg FLAC file into the chain.
     *
     * <p>Ogg FLAC metadata data writing is not supported yet and
     * MetadataChain.write() will fail.
     *
     * @param filename The path to the Ogg FLAC file to read.
     * @return {@code true} if a valid list of metadata blocks was read from
     *    {@code filename}, else {@code false}.  On failure, check the status with
     *    MetadataChain.status().
     */

    /* @@@@add to tests */
    public boolean readOgg(String filename) throws IOException {
        return readInternal(filename, true);
    }

    /**
     * Read all metadata from a FLAC stream into the chain via I/O callbacks.
     *
     *  The {@code callbacks} need only be open for reading, but must be seekable.
     *
     * The stream will NOT be closed after the metadata is read;
     * that is the duty of the caller.
     *
     * @param callbacks A set of callbacks to use for I/O.  The mandatory
     *                 callbacks are {@code read}, {@code seek}, and {@code tell}.
     * @return {@code true} if a valid list of metadata blocks was read from
     *    {@code callbacks}, else {@code false}.  On failure, check the status with
     *    MetadataChain.status().
     */
    public boolean readWithCallbacks(IOCallbacks callbacks) throws IOException {
        return readWithCallbacksInternal(callbacks, false);
    }

    /**
     * Read all metadata from an Ogg FLAC stream into the chain via I/O callbacks.
     *
     *  The {@code callbacks} need only be open for reading, but must be seekable.
     *
     * <p>Ogg FLAC metadata data writing is not supported yet and
     * MetadataChain.write() will fail.
     *
     * The stream will NOT be closed after the metadata is read;
     * that is the duty of the caller.
     *
     * @param callbacks A set of callbacks to use for I/O.  The mandatory
     *                 callbacks are {@code read}, {@code seek}, and {@code tell}.
     * @return {@code true} if a valid list of metadata blocks was read from
     *    {@code callbacks}, else {@code false}.  On failure, check the status with
     *    MetadataChain.status().
     */

    /* @@@@add to tests */
    public boolean readOggWithCallbacks(IOCallbacks callbacks) throws IOException {
        return readWithCallbacksInternal(callbacks, true);
    }

    private enum LastBlockState {
        NONE,
        SIZE_CHANGED,
        BLOCK_ADDED,
        BLOCK_REMOVED
    }

    /**
     * Checks if writing the given chain would require the use of a
     *  temporary file, or if it could be written in place.
     *
     *  Under certain conditions, padding can be utilized so that writing
     *  edited metadata back to the FLAC file does not require rewriting the
     *  entire file.  If rewriting is required, then a temporary workfile is
     *  required.  When writing metadata using callbacks, you must check
     *  this function to know whether to call
     *  MetadataChain.writeWithCallbacks() or
     *  MetadataChain.writeWithCallbacksAndTempfile().  When
     *  writing with MetadataChain.write(), the temporary file is
     *  handled internally.
     *
     * @param usePadding Whether or not padding will be allowed to be used
     *                 during the write.  The value of {@code usePadding} given
     *                 here must match the value later passed to
     *                 MetadataChain.writeWithCallbacks() or
     *                 MetadataChain.writeWithCallbacksAndTempfile().
     * @return {@code true} if writing the current chain would require a tempfile, or
     *    {@code false} if metadata can be written in place.
     */

    /*
     * This does all the same checks that are in prepareForWrite()
     * but doesn't actually alter the chain.  Make sure to update the logic
     * here if prepareForWrite() changes.
     */
    public boolean checkIfTempfileNeeded(boolean usePadding) {
        long currentLength = calculateLength();
        LastBlockState lbsState = LastBlockState.NONE;
        int lbsSize = 0;
        if (usePadding) {
            Node node = tail;
            /* if the metadata shrank and the last block is padding, we just extend the last padding block */
            if (currentLength < initialLength && node.data instanceof Padding) {
                lbsState = LastBlockState.SIZE_CHANGED;
                lbsSize = (int) (Integer.toUnsignedLong(node.data.getLength()) + initialLength - currentLength);
            } else if (currentLength + Format.STREAM_METADATA_HEADER_LENGTH <= initialLength) {
                /* if the metadata shrank more than 4 bytes then there's room to add another padding block */
                lbsState = LastBlockState.BLOCK_ADDED;
                lbsSize = (int) (initialLength - (currentLength + Format.STREAM_METADATA_HEADER_LENGTH));
            } else if (currentLength > initialLength) {
                /* if the metadata grew but the last block is padding, try cutting the padding to restore the original length so we don't have to rewrite the whole file */
                long delta = currentLength - initialLength;
                if (node.data instanceof Padding) {
                    /* if the delta is exactly the size of the last padding block, remove the padding block */
                    if (Integer.toUnsignedLong(node.data.getLength()) + Format.STREAM_METADATA_HEADER_LENGTH == delta) {
                        lbsState = LastBlockState.BLOCK_REMOVED;
                        lbsSize = 0;
                    } else if (Integer.toUnsignedLong(node.data.getLength()) >= delta) {
                        /* if there is at least 'delta' bytes of padding, trim the padding down */
                        lbsState = LastBlockState.SIZE_CHANGED;
                        lbsSize = (int) (Integer.toUnsignedLong(node.data.getLength()) - delta);
                    }
                }
            }
        }
        currentLength = 0;
        /* check sizes of all metadata blocks; reduce padding size if necessary */
        for (Node node = head; node != null; node = node.next) {
            int blockLen = node.data.getLength();
            if (node == tail) {
                if (lbsState == LastBlockState.BLOCK_REMOVED)
                    continue;
                else if (lbsState == LastBlockState.SIZE_CHANGED)
                    blockLen = lbsSize;
            }
            if (Integer.compareUnsigned(blockLen, 1 << Format.STREAM_METADATA_LENGTH_LEN) >= 0) {
                if (node.data instanceof Padding)
                    blockLen = (1 << Format.STREAM_METADATA_LENGTH_LEN) - 1;
                else
                    return false; /* the return value doesn't matter */
            }
            currentLength += Format.STREAM_METADATA_HEADER_LENGTH + Integer.toUnsignedLong(blockLen);
        }
        if (lbsState == LastBlockState.BLOCK_ADDED) {
            /* test added padding block */
            int blockLen = lbsSize;
            if (Integer.compareUnsigned(blockLen, 1 << Format.STREAM_METADATA_LENGTH_LEN) >= 0)
                blockLen = (1 << Format.STREAM_METADATA_LENGTH_LEN) - 1;
            currentLength += Format.STREAM_METADATA_HEADER_LENGTH + Integer.toUnsignedLong(blockLen);
        }
        return currentLength != initialLength;
    }

    /**
     * Write all metadata out to the FLAC file.  This function tries to be as
     *  efficient as possible; how the metadata is actually written is shown by
     *  the following:
     *
     *  If the current chain is the same size as the existing metadata, the new
     *  data is written in place.
     *
     *  If the current chain is longer than the existing metadata, and
     *  {@code usePadding} is {@code true}, and the last block is a PADDING block of
     *  sufficient length, the function will truncate the final padding block
     *  so that the overall size of the metadata is the same as the existing
     *  metadata, and then just rewrite the metadata.  Otherwise, if not all of
     *  the above conditions are met, the entire FLAC file must be rewritten.
     *  If you want to use padding this way it is a good idea to call
     *  MetadataChain.sortPadding() first so that you have the maximum
     *  amount of padding to work with, unless you need to preserve ordering
     *  of the PADDING blocks for some reason.
     *
     *  If the current chain is shorter than the existing metadata, and
     *  {@code usePadding} is {@code true}, and the final block is a PADDING block, the padding
     *  is extended to make the overall size the same as the existing data.  If
     *  {@code usePadding} is {@code true} and the last block is not a PADDING block, a new
     *  PADDING block is added to the end of the new data to make it the same
     *  size as the existing data (if possible, see the note to
     *  MetadataSimpleIterator.setBlock() about the four byte limit)
     *  and the new data is written in place.  If none of the above apply or
     *  {@code usePadding} is {@code false}, the entire FLAC file is rewritten.
     *
     *  If {@code preserveFileStats} is {@code true}, the owner and modification time will
     *  be preserved even if the FLAC file is written.
     *
     *  For this write function to be used, the chain must have been read with
     *  MetadataChain.read()/MetadataChain.readOgg(), not
     *  MetadataChain.readWithCallbacks()/MetadataChain.readOggWithCallbacks().
     *
     * @param usePadding See above.
     * @param preserveFileStats See above.
     * @return {@code true} if the write succeeded, else {@code false}.  On failure,
     *    check the status with MetadataChain.status().
     */
    public boolean write(boolean usePadding, boolean preserveFileStats) throws IOException {
        /* cannot write back to Ogg FLAC yet */
        if (isOgg)
            return fail(MetadataChainStatus.INTERNAL_ERROR);
        if (filename == null)
            return fail(MetadataChainStatus.READ_WRITE_MISMATCH);
        long currentLength = prepareForWrite(usePadding);
        /* a return value of 0 means there was an error; status is already set */
        if (currentLength == 0)
            return false;
        Path path = Path.of(filename);
        FileStats attrs = null;
        if (preserveFileStats)
            try {
                attrs = FileStats.read(path);
            } catch (IOException ignored) {

            }
        if (currentLength == initialLength) {
            rewriteMetadataInPlace();
        } else {
            if (!rewriteFile())
                return false;
            /* recompute lengths and offsets */
            initialLength = currentLength;
            lastOffset = firstOffset;
            for (Node node = head; node != null; node = node.next)
                lastOffset += Format.STREAM_METADATA_HEADER_LENGTH + Integer.toUnsignedLong(node.data.getLength());
        }
        if (attrs != null)
            attrs.restore(path);
        return true;
    }

    /**
     * Write all metadata out to a FLAC stream via callbacks.
     *
     *  (See MetadataChain.write() for the details on how padding is
     *  used to write metadata in place if possible.)
     *
     *  The {@code callbacks} must be open for updating and be seekable.
     *
     *  For this write function to be used, the chain must have been read with
     *  MetadataChain.readWithCallbacks()/MetadataChain.readOggWithCallbacks(),
     *  not MetadataChain.read()/MetadataChain.readOgg().
     *  Also, MetadataChain.checkIfTempfileNeeded() must have returned
     *  {@code false}.
     *
     * The stream will NOT be closed after the metadata is written;
     * that is the duty of the caller.
     *
     * @param usePadding See MetadataChain.write()
     * @param callbacks A set of callbacks to use for I/O.  The mandatory
     *                     callbacks are {@code write} and {@code seek}.
     * @return {@code true} if the write succeeded, else {@code false}.  On failure,
     *    check the status with MetadataChain.status().
     */
    public boolean writeWithCallbacks(boolean usePadding, IOCallbacks callbacks) throws IOException {
        /* cannot write back to Ogg FLAC yet */
        if (isOgg)
            return fail(MetadataChainStatus.INTERNAL_ERROR);
        if (filename != null)
            return fail(MetadataChainStatus.READ_WRITE_MISMATCH);
        if (checkIfTempfileNeeded(usePadding))
            return fail(MetadataChainStatus.WRONG_WRITE_CALL);
        /* a return value of 0 means there was an error; status is already set */
        if (prepareForWrite(usePadding) == 0)
            return false;
        rewriteMetadataInPlace(callbacks);
        return true;
    }

    /**
     * Write all metadata out to a FLAC stream via callbacks.
     *
     *  (See MetadataChain.write() for the details on how padding is
     *  used to write metadata in place if possible.)
     *
     *  This version of the write-with-callbacks function must be used when
     *  MetadataChain.checkIfTempfileNeeded() returns true.  In
     *  this function, you must supply I/O callbacks corresponding to the
     *  FLAC file to edit, and temporary callbacks to which the new FLAC
     *  file will be written.  It is the caller's job to move this temporary
     *  FLAC file on top of the original FLAC file to complete the metadata
     *  edit.
     *
     *  The {@code callbacks} must be open for reading and be seekable.  The {@code tempCallbacks} must be open for
     * writing.  It should be an empty stream, or at least positioned
     *  at the start-of-file (in which case it is the caller's duty to
     *  truncate it on return).
     *
     *  For this write function to be used, the chain must have been read with
     *  MetadataChain.readWithCallbacks()/MetadataChain.readOggWithCallbacks(),
     *  not MetadataChain.read()/MetadataChain.readOgg().
     *  Also, MetadataChain.checkIfTempfileNeeded() must have returned
     *  {@code true}.
     *
     * The streams will NOT be closed after the metadata is written;
     * that is the duty of the caller.
     *
     * @param usePadding See MetadataChain.write()
     * @param callbacks A set of callbacks to use for I/O on {@code callbacks}.
     *                     The mandatory callbacks are {@code read}, {@code seek}, and
     *                     {@code eof}.
     * @param tempCallbacks A set of callbacks to use for I/O on tempCallbacks.
     *                     The only mandatory callback is {@code write}.
     * @return {@code true} if the write succeeded, else {@code false}.  On failure,
     *    check the status with MetadataChain.status().
     */
    public boolean writeWithCallbacksAndTempfile(boolean usePadding, IOCallbacks callbacks, IOCallbacks tempCallbacks)
            throws IOException {
        /* cannot write back to Ogg FLAC yet */
        if (isOgg)
            return fail(MetadataChainStatus.INTERNAL_ERROR);
        if (filename != null)
            return fail(MetadataChainStatus.READ_WRITE_MISMATCH);
        if (!checkIfTempfileNeeded(usePadding))
            return fail(MetadataChainStatus.WRONG_WRITE_CALL);
        long currentLength = prepareForWrite(usePadding);
        /* a return value of 0 means there was an error; status is already set */
        if (currentLength == 0)
            return false;
        /* rewind */
        seek(callbacks, 0);
        if (!rewriteFile(callbacks, tempCallbacks))
            return false;
        /* recompute lengths and offsets */
        initialLength = currentLength;
        lastOffset = firstOffset;
        for (Node node = head; node != null; node = node.next)
            lastOffset += Format.STREAM_METADATA_HEADER_LENGTH + Integer.toUnsignedLong(node.data.getLength());
        return true;
    }

    /**
     * Merge adjacent PADDING blocks into a single block.
     *
     * <p>This function does not write to the FLAC file, it only
     * modifies the chain.
     *
     * <p>Any iterator on the current chain will become invalid after this
     * call.  You should get a new iterator.
     */
    public void mergePadding() {
        for (Node node = head; node != null;)
            if (!mergeAdjacentPadding(node))
                node = node.next;
    }

    /**
     * This function will move all PADDING blocks to the end on the metadata,
     *  then merge them into a single block.
     *
     * <p>This function does not write to the FLAC file, it only
     * modifies the chain.
     *
     * <p>Any iterator on the current chain will become invalid after this
     * call.  You should get a new iterator.
     */
    public void sortPadding() {
        Node node = head;
        /*
         * Don't try and be too smart... this simple algo is good enough for
         * the small number of nodes that we deal with.
         */
        for (int i = 0; i < nodes; i++) {
            if (node.data instanceof Padding) {
                Node save = node.next;
                removeNode(node);
                appendNode(node);
                node = save;
            } else
                node = node.next;
        }
        mergePadding();
    }

    private void clear() {
        head = tail = null;
        nodes = 0;
        filename = null;
        isOgg = false;
        status = MetadataChainStatus.OK;
        initialLength = 0;
    }

    private void appendNode(Node node) {
        node.next = node.prev = null;
        node.data.isLast = true;
        if (tail != null)
            tail.data.isLast = false;
        if (head == null)
            head = node;
        else {
            tail.next = node;
            node.prev = tail;
        }
        tail = node;
        nodes++;
    }

    private void removeNode(Node node) {
        if (node == head)
            head = node.next;
        else
            node.prev.next = node.next;
        if (node == tail)
            tail = node.prev;
        else
            node.next.prev = node.prev;
        if (tail != null)
            tail.data.isLast = true;
        nodes--;
    }

    void deleteNode(Node node) {
        removeNode(node);
    }

    private long calculateLength() {
        long length = 0;
        for (Node node = head; node != null; node = node.next)
            length += Format.STREAM_METADATA_HEADER_LENGTH + Integer.toUnsignedLong(node.data.getLength());
        return length;
    }

    /* return true iff node and node.next are both padding */
    private boolean mergeAdjacentPadding(Node node) {
        /* new block size can be greater than max metadata block size, but it'll be fixed later in prepareForWrite() */
        if (node.data instanceof Padding padding && node.next != null && node.next.data instanceof Padding next) {
            padding.length += Format.STREAM_METADATA_HEADER_LENGTH + next.length;
            deleteNode(node.next);
            return true;
        }
        return false;
    }

    /* Returns the new length of the chain, or 0 if there was an error. */

    /*
     * WATCHOUT: This can get called multiple times before a write, so
     * it should still work when this happens.
     */

    /*
     * WATCHOUT: Make sure to also update the logic in
     * checkIfTempfileNeeded() if the logic here changes.
     */
    private long prepareForWrite(boolean usePadding) {
        if (head == null)
            throw new IllegalStateException("chain has not been read");
        long currentLength = calculateLength();
        /* Check all isLast settings on the blocks */
        for (Node i = head; i.next != null; i = i.next)
            i.data.isLast = false;
        tail.data.isLast = true;
        if (usePadding) {
            /* if the metadata shrank and the last block is padding, we just extend the last padding block */
            if (currentLength < initialLength && tail.data instanceof Padding padding) {
                long delta = initialLength - currentLength;
                padding.length = (int) (Integer.toUnsignedLong(padding.length) + delta);
                currentLength += delta;
            } else if (currentLength + Format.STREAM_METADATA_HEADER_LENGTH <= initialLength) {
                /* if the metadata shrank more than 4 bytes then there's room to add another padding block */
                Padding padding = new Padding(
                        (int) (initialLength - (Format.STREAM_METADATA_HEADER_LENGTH + currentLength)));
                Node node = new Node();
                node.data = padding;
                appendNode(node);
                currentLength = calculateLength();
            } else if (currentLength > initialLength) {
                /* if the metadata grew but the last block is padding, try cutting the padding to restore the original length so we don't have to rewrite the whole file */
                long delta = currentLength - initialLength;
                if (tail.data instanceof Padding padding) {
                    /* if the delta is exactly the size of the last padding block, remove the padding block */
                    if (Integer.toUnsignedLong(padding.length) + Format.STREAM_METADATA_HEADER_LENGTH == delta) {
                        deleteNode(tail);
                        currentLength = calculateLength();
                    } else if (Integer.toUnsignedLong(padding.length) >= delta) {
                        /* if there is at least 'delta' bytes of padding, trim the padding down */
                        padding.length -= (int) delta;
                        currentLength -= delta;
                    }
                }
            }
        }
        /* check sizes of all metadata blocks; reduce padding size if necessary */
        for (Node node = head; node != null; node = node.next) {
            if (Integer.compareUnsigned(node.data.getLength(), 1 << Format.STREAM_METADATA_LENGTH_LEN) >= 0) {
                if (node.data instanceof Padding padding) {
                    padding.length = (1 << Format.STREAM_METADATA_LENGTH_LEN) - 1;
                    currentLength = calculateLength();
                } else {
                    status = MetadataChainStatus.BAD_METADATA;
                    return 0;
                }
            }
        }
        return currentLength;
    }

    private boolean fail(MetadataChainStatus s) {
        status = s;
        return false;
    }

    private void seek(IOCallbacks callbacks, long offset) throws IOException {
        try {
            callbacks.seek(offset);
        } catch (IOException e) {
            status = MetadataChainStatus.SEEK_ERROR;
            throw e;
        }
    }

    private boolean read(IOCallbacks callbacks) throws IOException {
        MetadataFileIO.Reader input = new MetadataFileIO.Reader(callbacks, 0);
        try {
            /* we assume we're already at the beginning of the file */
            if (!input.seekToFirstMetadataBlock())
                return fail(MetadataChainStatus.NOT_A_FLAC_FILE);
            long position = input.tell();
            firstOffset = position;
            MetadataFileIO.Header header;
            do {
                header = input.readHeader();
                Node node = new Node();
                node.data = input.readData(header);
                status = MetadataChainStatus.OK;
                appendNode(node);
            } while (!header.isLast());
            position = input.tell();
            lastOffset = position;
            if (!(head.data instanceof StreamInfo))
                return fail(MetadataChainStatus.BAD_METADATA);
            initialLength = calculateLength();
            return true;
        } catch (MetadataFileIO.Failure e) {
            return fail(getEquivalentStatus(e.status));
        } catch (IOException e) {
            status = getEquivalentStatus(input.failureStatus());
            throw e;
        }
    }

    private boolean readOgg(IOCallbacks callbacks) throws IOException {
        try (StreamDecoder decoder = new StreamDecoder()) {
            decoder.setMetadataRespondAll();
            StreamDecoderInitStatus init = decoder.initOggStream((d, buffer, offset, length) -> {
                if (length == 0 || status != MetadataChainStatus.OK)
                    return -2;
                int n = callbacks.read(buffer, offset, length);
                return n == 0 ? -1 : n;
            }, null, null, null, null, (d, frame, buffer) -> false, (d, block) -> {
                if (block.typeCode > Format.MAX_METADATA_TYPE_CODE) {

                    status = MetadataChainStatus.MEMORY_ALLOCATION_ERROR;
                    return;
                }
                Node node = new Node();
                node.data = block.copy();
                appendNode(node);
            }, (d, error) -> status = MetadataChainStatus.INTERNAL_ERROR);
            if (init != StreamDecoderInitStatus.OK)
                return fail(MetadataChainStatus.INTERNAL_ERROR);
            /* @@@ wrong; will need to be set correctly to implement metadata writing for Ogg FLAC */
            firstOffset = 0;
            if (!decoder.processUntilEndOfMetadata())
                /* @@@ maybe needs better error code */
                status = MetadataChainStatus.INTERNAL_ERROR;
            if (status != MetadataChainStatus.OK)
                return false;
            lastOffset = 0;
            initialLength = calculateLength();
            /* Ogg FLAC file must have at least streaminfo and vorbis comment */
            if (initialLength == 0 || !(head.data instanceof StreamInfo))
                return fail(MetadataChainStatus.BAD_METADATA);
            return true;
        } catch (IOException e) {
            status = MetadataChainStatus.INTERNAL_ERROR;
            throw e;
        }
    }

    private void writeBlocks(IOCallbacks callbacks) throws IOException {
        try {
            for (Node node = head; node != null; node = node.next) {
                MetadataFileIO.writeHeader(callbacks, node.data);
                MetadataFileIO.writeData(callbacks, node.data);
            }
        } catch (IOException e) {
            status = MetadataChainStatus.WRITE_ERROR;
            throw e;
        }
    }

    private void rewriteMetadataInPlace(IOCallbacks callbacks) throws IOException {
        seek(callbacks, firstOffset);
        writeBlocks(callbacks);
        status = MetadataChainStatus.OK;
    }

    private void rewriteMetadataInPlace() throws IOException {
        RandomAccessFile file;
        try {
            if (!Files.exists(Path.of(filename)))
                throw new FileNotFoundException(filename);
            file = new RandomAccessFile(filename, "rw");
        } catch (IOException e) {
            status = MetadataChainStatus.ERROR_OPENING_FILE;
            throw e;
        }
        try (file) {
            rewriteMetadataInPlace(new MetadataFileIO.FileCallbacks(file));
        }
    }

    private boolean rewriteFile() throws IOException {
        Path path = Path.of(filename);
        RandomAccessFile input;
        try {
            input = new RandomAccessFile(filename, "r");
        } catch (IOException e) {
            status = MetadataChainStatus.ERROR_OPENING_FILE;
            throw e;
        }
        Path temp = null;
        boolean moved = false;
        try {
            try (input) {
                RandomAccessFile output;
                try {
                    temp = FileStats.temporary(path.toAbsolutePath().getParent());
                    output = new RandomAccessFile(temp.toFile(), "rw");
                } catch (IOException e) {
                    status = MetadataChainStatus.ERROR_OPENING_FILE;
                    throw e;
                }
                try (output) {
                    if (!rewriteFile(new MetadataFileIO.FileCallbacks(input), new MetadataFileIO.FileCallbacks(output)))
                        return false;
                }
            }
            /* move the tempfile on top of the original */
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved)
                FileStats.cleanupTempfile(temp);
        }
        return true;
    }

    /* assumes 'callbacks' is already at beginning of file */
    private boolean rewriteFile(IOCallbacks callbacks, IOCallbacks tempCallbacks) throws IOException {
        try {
            /* copy the file prefix (data up to first metadata block */
            copyNBytesFromFile(callbacks, tempCallbacks, firstOffset);
            /* write the metadata */
            writeBlocks(tempCallbacks);
            /* copy the file postfix (everything after the metadata) */
            seek(callbacks, lastOffset);
            return copyRemainingBytesFromFile(callbacks, tempCallbacks);
        } catch (MetadataFileIO.Failure e) {
            return fail(getEquivalentStatus(e.status));
        }
    }

    private boolean readInternal(String filename, boolean isOgg) throws IOException {
        clear();
        this.filename = filename;
        this.isOgg = isOgg;
        RandomAccessFile file;
        try {
            file = new RandomAccessFile(filename, "r");
        } catch (IOException e) {
            status = MetadataChainStatus.ERROR_OPENING_FILE;
            throw e;
        }
        try (file) {
            IOCallbacks io = new MetadataFileIO.FileCallbacks(file);
            return isOgg ? readOgg(io) : read(io);
        }
    }

    private boolean readWithCallbacksInternal(IOCallbacks callbacks, boolean isOgg) throws IOException {
        clear();
        this.isOgg = isOgg;
        /* rewind */
        seek(callbacks, 0);
        return isOgg ? readOgg(callbacks) : read(callbacks);
    }

    private void copyNBytesFromFile(IOCallbacks callbacks, IOCallbacks tempCallbacks, long bytes) throws IOException {
        byte[] buffer = new byte[8192];
        while (bytes > 0) {
            int n = (int) Math.min(buffer.length, bytes);
            int read;
            try {
                read = callbacks.read(buffer, 0, n);
            } catch (IOException e) {
                status = MetadataChainStatus.READ_ERROR;
                throw e;
            }
            if (read != n)
                throw new MetadataFileIO.Failure(MetadataSimpleIteratorStatus.READ_ERROR);
            try {
                tempCallbacks.write(buffer, 0, n);
            } catch (IOException e) {
                status = MetadataChainStatus.WRITE_ERROR;
                throw e;
            }
            bytes -= n;
        }
    }

    private boolean copyRemainingBytesFromFile(IOCallbacks callbacks, IOCallbacks tempCallbacks) throws IOException {
        byte[] buffer = new byte[8192];
        while (true) {
            int n;
            try {
                if (callbacks.eof())
                    break;
                n = callbacks.read(buffer, 0, buffer.length);

                if (n < 0)
                    break;
                if (n == 0 && !callbacks.eof())
                    return fail(MetadataChainStatus.READ_ERROR);
            } catch (IOException e) {
                status = MetadataChainStatus.READ_ERROR;
                throw e;
            }
            if (n > 0)
                try {
                    tempCallbacks.write(buffer, 0, n);
                } catch (IOException e) {
                    status = MetadataChainStatus.WRITE_ERROR;
                    throw e;
                }
        }
        return true;
    }

    private static MetadataChainStatus getEquivalentStatus(MetadataSimpleIteratorStatus status) {
        return MetadataChainStatus.valueOf(status.name());
    }

}
