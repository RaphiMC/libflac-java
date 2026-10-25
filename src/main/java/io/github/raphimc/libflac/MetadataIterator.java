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

/** The level 2 iterator type. */
public final class MetadataIterator {
    private MetadataChain chain;
    private MetadataChain.Node current;

    /**
     * Initialize the iterator to point to the first metadata block in the
     *  given chain.
     *
     * @param chain An existing and initialized (read) chain.
     */
    public void init(MetadataChain chain) {
        if (chain.head == null)
            throw new IllegalStateException("empty chain");
        this.chain = chain;
        current = chain.head;
    }

    /**
     * Moves the iterator forward one metadata block, returning {@code false} if
     *  already at the end.
     *
     * @return {@code false} if already at the last metadata block of the chain, else
     *    {@code true}.
     */
    public boolean next() {
        if (current == null || current.next == null)
            return false;
        current = current.next;
        return true;
    }

    /**
     * Moves the iterator backward one metadata block, returning {@code false} if
     *  already at the beginning.
     *
     * @return {@code false} if already at the first metadata block of the chain, else
     *    {@code true}.
     */
    public boolean prev() {
        if (current == null || current.prev == null)
            return false;
        current = current.prev;
        return true;
    }

    /**
     * Get the type of the metadata block at the current position.
     *
     * @return The type of the metadata block at the current iterator position.
     */
    public MetadataType getBlockType() {
        return getBlock().getType();
    }

    /**
     * Get the metadata block at the current position.  You can modify
     * the block in place but must write the chain before the changes
     * are reflected to the FLAC file.  You do not need to call
     * MetadataIterator.setBlock() to reflect the changes;
     * the reference returned by MetadataIterator.getBlock()
     * refers directly to the block in the chain.
     *
     * @return The current metadata block.
     */
    public StreamMetadata getBlock() {
        return current.data;
    }

    /**
     * Set the metadata block at the current position, replacing the existing
     *  block.  The new block passed in becomes owned by the chain.
     *
     * @param block A metadata block.
     * @return {@code false} if the conditions in the above description are not met, otherwise {@code true}.
     */
    public boolean setBlock(StreamMetadata block) {
        return deleteBlock(false) && insertBlockAfter(block);
    }

    /**
     * Removes the current block from the chain.  If {@code replaceWithPadding} is
     *  {@code true}, the block will instead be replaced with a padding block of equal
     *  size.  You can not delete the STREAMINFO block.  The iterator will be
     *  left pointing to the block before the one just "deleted", even if
     *  {@code replaceWithPadding} is {@code true}.
     *
     * @param replaceWithPadding See above.
     * @return {@code false} if the conditions in the above description are not met,
     *    otherwise {@code true}.
     */
    public boolean deleteBlock(boolean replaceWithPadding) {
        if (current.prev == null)
            return false;
        MetadataChain.Node save = current.prev;
        if (replaceWithPadding) {
            Padding padding = new Padding(current.data.getLength());
            padding.isLast = current.data.isLast;
            current.data = padding;
        } else
            chain.deleteNode(current);
        current = save;
        return true;
    }

    /**
     * Insert a new block before the current block.  You cannot insert a block
     *  before the first STREAMINFO block.  You cannot insert a STREAMINFO block
     *  as there can be only one, the one that already exists at the head when you
     *  read in a chain.  The chain takes ownership of the new block.  The iterator will be left pointing to
     *  the new block.
     *
     * @param block A metadata block to insert.
     * @return {@code false} if the conditions in the above description are not met, otherwise {@code true}.
     */
    public boolean insertBlockBefore(StreamMetadata block) {
        if (block instanceof StreamInfo || current.prev == null)
            return false;
        MetadataChain.Node node = new MetadataChain.Node();
        node.data = block;
        insertNode(node);
        current = node;
        return true;
    }

    /**
     * Insert a new block after the current block.  You cannot insert a STREAMINFO
     *  block as there can be only one, the one that already exists at the head when
     *  you read in a chain.  The chain takes ownership of the new block.  The iterator will be left pointing to
     *  the new block.
     *
     * @param block A metadata block to insert.
     * @return {@code false} if the conditions in the above description are not met, otherwise {@code true}.
     */
    public boolean insertBlockAfter(StreamMetadata block) {
        if (block instanceof StreamInfo)
            return false;
        MetadataChain.Node node = new MetadataChain.Node();
        node.data = block;
        insertNodeAfter(node);
        current = node;
        return true;
    }

    private void insertNode(MetadataChain.Node node) {
        node.data.isLast = false;
        node.prev = current.prev;
        node.next = current;
        if (node.prev == null)
            chain.head = node;
        else
            node.prev.next = node;
        current.prev = node;
        chain.nodes++;
    }

    private void insertNodeAfter(MetadataChain.Node node) {
        current.data.isLast = false;
        node.prev = current;
        node.next = current.next;
        if (node.next == null)
            chain.tail = node;
        else
            node.next.prev = node;
        node.prev.next = node;
        chain.tail.data.isLast = true;
        chain.nodes++;
    }
}
