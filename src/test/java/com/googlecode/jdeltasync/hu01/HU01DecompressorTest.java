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

import static org.junit.Assert.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Ignore;
import org.junit.Test;


/**
 * Tests {@link HU01Decompressor}
 */
public class HU01DecompressorTest {

    @Test
    public void testDecompress1() throws Exception {
        decompress("test1.hu01", "test1.plain");
    }
    
    @Test
    public void testDecompress2() throws Exception {
        decompress("test2.hu01", "test2.plain");
    }
    
    @Test
    public void testDecompress3() throws Exception {
        decompress("test3.hu01", "test3.plain");
    }
    
    @Test
    public void testDecompress4() throws Exception {
        decompress("test4.hu01", "test4.plain");
    }
    
    @Test
    public void testDecompress5() throws Exception {
        decompress("test5.hu01", "test5.plain");
    }
    
    @Test
    public void testDecompress6() throws Exception {
        decompress("test6.hu01", "test6.plain");
    }
    
    @Test
    public void testDecompress7() throws Exception {
        /*
         * This HU01 file contains an uncompressed block.
         */
        decompress("test7.hu01", "test7.plain");
    }
    
    @Test
    @Ignore
    public void testDecompressLkml2009() throws Exception {
        /*
         * To run this test you need to download the lkml2009.tar.bz2 archive
         * from the JDeltaSync web site and extract it to the source code folder.
         * After unpacking there should be a folder named lkml2009 in the root
         * of the source folder. Also, you will have to remove the @Ignore annotation.
         */
        File dir = new File("lkml2009");
        int i = 0;
        for (File hu01 : dir.listFiles()) {
            if (!hu01.getName().endsWith(".hu01")) {
                continue;
            }
            File plain = new File(dir, hu01.getName().substring(0, hu01.getName().length() - 5) + ".plain");
            System.out.println((++i) + ": " +  hu01);
            InputStream hu01Input = new BufferedInputStream(new FileInputStream(hu01));
            InputStream plainInput = new BufferedInputStream(new FileInputStream(plain));
            try  {
                decompress(hu01Input, plainInput);
            } finally {
                try {
                    hu01Input.close();
                } catch (Throwable t) {}
                try {
                    plainInput.close();
                } catch (Throwable t) {}
            }
        }
    }
    
    private void decompress(String hu01, String plain) throws Exception {
        decompress(getClass().getResourceAsStream(hu01), getClass().getResourceAsStream(plain));
    }
    
    private void decompress(InputStream hu01Input, InputStream plainInput) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        byte[] decoded = new byte[1024];
        HU01Decompressor decompressor = new HU01Decompressor();
        while (true) {
            int n = hu01Input.read(data);
            if (n == -1) {
                break;
            }
            assertFalse(decompressor.finished());
            decompressor.addInput(data, 0, n);
            while ((n = decompressor.decompress(decoded, 0, decoded.length)) > 0) {
                baos.write(decoded, 0, n);
            }
        }
        assertTrue(decompressor.finished());
        String expected = new String(toByteArray(plainInput));
        String actual = new String(baos.toByteArray(), "UTF-8");
        assertEquals(expected, actual);
    }
    
    static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        long count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return output.toByteArray();
    }
}
