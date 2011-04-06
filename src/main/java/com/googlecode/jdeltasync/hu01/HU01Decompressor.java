/*
 * Copyright (c) 2011, the JDeltaSync project. All Rights Reserved.
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
package com.googlecode.jdeltasync.hu01;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import java.util.zip.Inflater;

/**
 * Decompresses data compressed using the proprietary <i>HU01</i> algorithm
 * (see <a href="http://en.wikipedia.org/wiki/DeltaSync">Wikipedia's DeltaSync page</a>
 * for info on the compression). This class resembles the {@link Inflater} class 
 * and is used in a similar manner.
 * <p>
 * Some of the JavaDoc comments have been copied from Apache Harmony's 
 * {@code java.util.zip.Inflater} code.
 */
public class HU01Decompressor {
    private static final int HU01_MAGIC = 0x31305548; // HU01 (LE)
    private static final int SCBH_MAGIC = 0x48424353; // SCBH (LE)
    private static final int TABLE_SIZE = 256;

    private boolean inHeader = true;
    private long decompressedSize = 0;
    private long decompressedBytes = 0;
    private ByteBuffer buffer;
    private ByteBuffer decoded;
    private short[] table = new short[0x8000];
    
    /**
     * Creates a new instance using an initial buffer size of 4096 bytes.
     */
    public HU01Decompressor() {
        this(4096);
    }
    
    /**
     * Creates a new instance using the specified initial buffer size.
     * 
     * @param initialBufferSize the initail buffer size.
     */
    public HU01Decompressor(int initialBufferSize) {
        buffer = allocateBuffer(initialBufferSize);
        buffer.flip();
    }
    
    /**
     * Resets the {@code HU01Decompressor}. Should be called prior to inflating a new
     * set of data.
     */
    public void reset() {
        inHeader = true;
        decompressedSize = 0;
        decompressedBytes = 0;
        buffer.position(0);
        buffer.limit(0);
        if (decoded != null) {
            decoded.position(0);
            decoded.limit(0);
        }
    }
    
    /**
     * Indicates if the {@code HU01Decompressor} has decompressed the entire compressed
     * stream. If compressed bytes remain this method will return {@code false}. This 
     * method should be called after all compressed input is supplied to the 
     * {@code HU01Decompressor}.
     *
     * @return {@code true} if all input has been decompressed, {@code false}
     *         otherwise.
     */
    public boolean finished() {
        return !inHeader && decompressedBytes == decompressedSize;
    }
    
    /**
     * Decompresses bytes from current input and stores them in {@code buf}.
     *
     * @param buf the buffer where decompressed data bytes are written.
     * @throws HU01Exception if the underlying stream is corrupted.
     * @return the number of bytes decompressed. Returns 0 if more data is needed.
     *         If 0 is returned {@link #addInput(byte[])} or {@link #addInput(byte[], int, int)}
     *         has to be called to provide more compressed data.
     */
    public int decompress(byte[] buf) throws HU01Exception {
        return decompress(buf, 0, buf.length);
    }
    
    /**
     * Decompresses up to {@code nbytes} bytes from the current input and stores them in {@code
     * buf} starting at {@code off}.
     *
     * @param buf the buffer to write decompressed bytes to.
     * @param off the offset in buffer where to start writing decompressed data.
     * @param nbytes the number of decompressed bytes to write to {@code buf}.
     * @throws HU01Exception if the underlying stream is corrupted.
     * @return the number of bytes decompressed. Returns 0 if more data is needed.
     *         If 0 is returned {@link #addInput(byte[])} or {@link #addInput(byte[], int, int)}
     *         has to be called to provide more compressed data.
     */
    public int decompress(byte[] buf, int off, int nbytes) throws HU01Exception {
        if (finished()) {
            return -1;
        }
        
        if (inHeader) {
            long ret = header();
            if (ret == -1) {
                return 0;
            }
            inHeader = false;
            decompressedSize = ret;
        }
        
        if (decoded == null || !decoded.hasRemaining()) {
            if (block() == 0) {
                return 0;
            }
        }
        
        if (decoded.hasRemaining()) {
            int n = Math.min(nbytes, decoded.remaining());
            decoded.get(buf, off, n);
            decompressedBytes += n;
            return n;
        }

        return 0;
    }
    
    /**
     * Adds input to be decompressed. This method should be
     * called if {@link #decompress(byte[])} or 
     * {@link #decompress(byte[], int, int)} return 0.
     *
     * @param buf the input buffer.
     */
    public void addInput(byte[] buf) {
        addInput(buf, 0, buf.length);
    }

    /**
     * Adds input to be decompressed. This method should be
     * called if {@link #decompress(byte[])} or 
     * {@link #decompress(byte[], int, int)} return 0.
     *
     * @param buf the input buffer.
     * @param off the offset to read from the input buffer.
     * @param nbytes the number of bytes to read.
     */
    public void addInput(byte[] buf, int off, int len) {
        if (buf == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off > buf.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        ensureCapacity(len);
        int position = buffer.position();
        int limit = buffer.limit();
        buffer.limit(buffer.capacity());
        buffer.position(limit);
        buffer.put(buf, off, len);
        buffer.position(position);
        buffer.limit(limit + len);
    }
    
    private ByteBuffer allocateBuffer(int len) {
        ByteBuffer b = ByteBuffer.wrap(new byte[len]);
        b.order(ByteOrder.LITTLE_ENDIAN);
        return b;
    }
    
    private void ensureCapacity(int n) {
        if (n > buffer.capacity() - buffer.limit()) {
            // There's not room for n bytes after the current limit
            if (buffer.position() != 0 && n < buffer.capacity() - buffer.remaining()) {
                // The bytes will fit if we shift the contents of buffer to position 0
                buffer.compact();
                buffer.flip();
            } else {
                // Create a new ByteBuffer big enough for the remaining data in buffer and n new bytes
                int newCapacity = buffer.capacity() * 2;
                while (newCapacity < buffer.remaining() + n) {
                    newCapacity *= 2;
                }
                ByteBuffer newBuffer = allocateBuffer(newCapacity);
                newBuffer.put(buffer);
                newBuffer.flip();
                buffer = newBuffer;
            }
        }
    }
    
    private long header() throws HU01Exception {
        ByteBuffer b = buffer.slice(); // slice to prevent changing buffer until we now we've got the full header 
        b.order(buffer.order());
        if (b.remaining() < 36) {
            // We need at least 36 bytes
            return -1;
        }
        int magic = b.getInt(0);
        if (magic != HU01_MAGIC) {
            char[] chars = new char[] {(char) (magic & 0xff), (char) ((magic >> 8) & 0xff), (char) ((magic >> 16) & 0xff), (char) ((magic >> 24) & 0xff)};
            throw new HU01Exception("Bad header: 'HU01' expected at beginning of header (was " + new String(chars) + ")");
        }
        int headerSize = b.getInt(4);
        if (headerSize < 0x28) {
            throw new HU01Exception("Bad header: Header size must be at least 0x28 bytes (was 0x" + Integer.toHexString(headerSize) + ")");
        }
        
        if (b.remaining() < headerSize) {
            return -1;
        }
        
        long size = b.getInt(32) & 0xffffffffL;
        buffer.position(headerSize);
        return size;
    }
    
    private int block() throws HU01Exception {
        ByteBuffer b = buffer.slice(); // slice to prevent changing buffer until we now we've got a full block 
        b.order(buffer.order());
        if (b.remaining() < 20) {
            // We need at least 20 bytes for the header
            return 0;
        }
        int magic = b.getInt(0);
        if (magic != SCBH_MAGIC) {
            char[] chars = new char[] {(char) (magic & 0xff), (char) ((magic >> 8) & 0xff), (char) ((magic >> 16) & 0xff), (char) ((magic >> 24) & 0xff)};
            throw new HU01Exception("Bad block header: 'SCBH' expected at beginning of block header (was " + new String(chars) + ")");
        }
        int headerSize = b.getInt(4);
        int decompressedBlockSize = b.getInt(8);
        long crc = b.getInt(12) & 0xffffffffL;
        int compressedBlockSize = b.getInt(16);
        if (b.remaining() < headerSize + compressedBlockSize) {
            // We need at least headerSize+compressedBlockSize bytes for the entire block
            return 0;
        }
        
        if (decoded == null || decoded.capacity() < decompressedBlockSize) {
            decoded = allocateBuffer(decompressedBlockSize);
        }
        decoded.clear();
        
        if (compressedBlockSize == decompressedBlockSize && decompressedBlockSize < 2048) {
            /*
             * Block isn't compressed. Just copy the bytes. We don't know how to properly check for 
             * uncompressed blocks. For now the check above seems to work.
             */
            b.position(headerSize);
            b.limit(headerSize + compressedBlockSize);
            decoded.put(b);
        } else {
            // Compressed block
            b.position(headerSize);
            b.limit(b.position() + TABLE_SIZE);
            if (!HU01.build_decompression_table(b.slice(), table)) {
                throw new HU01Exception("Bad block table");
            }
        
            b.position(headerSize + TABLE_SIZE);
            b.limit(headerSize + compressedBlockSize);
            HU01.decompress_hu01_block(b.slice(), table, decoded);
        }
        
        decoded.position(0);
        decoded.limit(decompressedBlockSize);
        
        buffer.position(buffer.position() + headerSize + compressedBlockSize);
        
        CRC32 crc32 = new CRC32();
        crc32.update(decoded.array(), decoded.position(), decoded.limit());
        if (crc32.getValue() != crc) {
            throw new HU01Exception("CRC check failed for block. Expected " + Long.toHexString(crc) 
                    + ". Was " + Long.toHexString(crc32.getValue()) + ".");
        }
        
        return decompressedBlockSize;
    }
}
