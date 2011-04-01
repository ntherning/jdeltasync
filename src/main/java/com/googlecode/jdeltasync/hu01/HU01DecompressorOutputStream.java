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

package com.googlecode.jdeltasync.hu01;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Inflater;

/**
 * This class provides an implementation of {@code FilterOutputStream} that
 * uncompresses data that was compressed using the proprietary <i>HU01</i> algorithm
 * (see <a href="http://en.wikipedia.org/wiki/DeltaSync">Wikipedia's DeltaSync page</a>
 * for info on the compression). Basically it wraps a {@code HU01Decompressor} 
 * class and takes care of the buffering.
 * <p>
 * The source for this class was copied from Apache Harmony's {@code java.util.zip.InflaterOutputStream}
 * and modified to use {@link HU01Decompressor} instead of {@link Inflater}.
 *
 * @see HU01Decompressor
 */
public class HU01DecompressorOutputStream extends FilterOutputStream {

    /**
     * The {@link HU01Decompressor} used by {@link HU01DecompressorOutputStream}
     * to decompress data.
     */
    protected final HU01Decompressor decompressor;

    /**
     * The internal output buffer.
     */
    protected final byte[] buf;

    private boolean closed = false;

    private static final int DEFAULT_BUFFER_SIZE = 1024;

    /**
     * Constructs an {@link HU01DecompressorOutputStream} with the default 
     * {@link HU01Decompressor} and internal output buffer size.
     * 
     * @param out the output stream that {@link HU01DecompressorOutputStream} will write
     *            decompressed data into.
     */
    public HU01DecompressorOutputStream(OutputStream out) {
        this(out, new HU01Decompressor());
    }

    /**
     * Constructs an {@link HU01DecompressorOutputStream} with the specified 
     * {@link HU01Decompressor} and the default internal output buffer size.
     * 
     * @param out the output stream that {@link HU01DecompressorOutputStream} will write
     *            decompressed data into.
     * @param decompressor the {@link HU01Decompressor} used by the {@link HU01DecompressorOutputStream}
     *             to decompress data.
     */
    public HU01DecompressorOutputStream(OutputStream out, HU01Decompressor decompressor) {
        this(out, decompressor, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Constructs an {@link HU01DecompressorOutputStream} with the specified 
     * {@link HU01Decompressor} and internal output buffer size.
     * 
     * @param out the output stream that {@link HU01DecompressorOutputStream} will write
     *            decompressed data into.
     * @param decompressor the {@link HU01Decompressor} used by the {@link HU01DecompressorOutputStream}
     *             to decompress data.
     * @param bufLen the size of the internal output buffer.
     */
    public HU01DecompressorOutputStream(OutputStream out, HU01Decompressor decompressor, int bufLen) {
        super(out);
        if (null == out || null == decompressor) {
            throw new NullPointerException();
        }
        if (bufLen <= 0) {
            throw new IllegalArgumentException();
        }
        this.decompressor = decompressor;
        buf = new byte[bufLen];
    }

    /**
     * Writes remaining data into the output stream and closes the underlying
     * output stream data.
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            finish();
            out.close();
            closed = true;
        }
    }

    /**
     * Flushes the output stream.
     */
    @Override
    public void flush() throws IOException {
        finish();
        out.flush();
    }

    /**
     * Finishes writing current uncompressed data into the {@link HU01DecompressorOutputStream}
     * but doesn't closes it.
     * 
     * @throws IOException if the stream has been closed or some I/O error occurs.
     */
    public void finish() throws IOException {
        checkClosed();
        write();
    }

    /**
     * Writes a byte to the uncompressing output stream.
     * 
     * @param b
     *            the bit to write to the uncompressing output stream.
     * @throws IOException
     *             if the stream has been closed or some I/O error occurs.
     */
    @Override
    public void write(int b) throws IOException {
        write(new byte[] { (byte) b }, 0, 1);
    }

    /**
     * Writes bytes to the uncompressing output stream.
     * 
     * @param b
     *            the byte array to write to the uncompressing output stream.
     * @param off
     *            the offset in the byte array where the data is first to be
     *            uncompressed.
     * @param len
     *            the number of the bytes to be uncompressed.
     * @throws IOException
     *             if the stream has been closed or some I/O error occurs.
     * @throws NullPointerException
     *             if the byte array is null.
     * @throws IndexOutOfBoundsException
     *             if the off less than zero or len less than zero or off + len
     *             is greater than the byte array length.
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkClosed();
        if (null == b) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }

        decompressor.addInput(b, off, len);
        write();
    }

    private void write() throws IOException {
        int n = 0;
        try {
            while ((n = decompressor.decompress(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } catch (HU01Exception e) {
            throw (IOException) new IOException().initCause(e);
        }
    }

    private void checkClosed() throws IOException {
        if (closed) {
            throw new IOException();
        }
    }
}
