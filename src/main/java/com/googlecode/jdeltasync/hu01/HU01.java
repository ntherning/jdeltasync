/*
 * (c) Copyright 2011 Daniel Parnell. Some Rights Reserved.
 * This work is licensed under a Creative Commons Attribution 3.0 Unported License.
 */
package com.googlecode.jdeltasync.hu01;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Java port of Daniel Parnell's hu01_decompressor.c code. The original code can be found at
 * <a href="https://github.com/dparnell/hu01">github</a>.
 * <p>
 * This class has package private scope
 * since it's not meant to be used directly. Use {@link HU01Decompressor},
 * {@link HU01DecompressorInputStream} or {@link HU01DecompressorOutputStream} instead. 
 */
class HU01 {

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_TABLE_GEN = false;

    static boolean build_decompression_table(ByteBuffer source_ptr, short[] table) {
        ByteBuffer source = source_ptr.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        
        int v2; // ecx@1
        int v3; // eax@4
        int v4; // ecx@4
        int v5; // eax@5
        int v6; // eax@8
        int v7; // ecx@8
        int v8; // eax@10
        int v9; // edi@10
        int v10; // eax@11
        int v11; // ecx@11
        //char *v12; // esi@11
        int v13; // ecx@12
        int v14; // eax@14
        int v15; // ecx@14
        int i; // edx@15
        int j; // eax@16
        int k; // edx@18
        int v19; // eax@21
        short v20; // si@21
        int v22; // eax@2
        int v23; // of@17
        int v24; // zf@17
        int v25; // sf@17
        short v26; // si@26
        int[] counts = new int[16]; // [sp+40h] [bp-44h]@1
        int total_counts; // [sp+80h] [bp-4h]@10
        int[] counts2 = new int[16]; // [sp+0h] [bp-84h]@16
           
        // 1bc52
        Arrays.fill(counts, 0);
        // 1bc62
        v2 = 256;
        // 1bc67
        do {
            // 1bc67
            v22 = source.get(v2 - 1) & 0xff;
            // 1bc6f
            v2--;
            // 1bc72
            counts[v22 & 0xF]++;
            // 1bc7c
            counts[v22 >> 4]++;
            // 1bc84
        } while ( v2 != 0 );

        if (DEBUG_TABLE_GEN) {    
            System.out.printf("\n\n1: HEX digit counts:\n");
            for (int index = 0; index < counts.length; index++) {
                System.out.printf("\t0x%x = 0x%08X\n", index, counts[index]);
            }
        }
        
        // 1bc90
        if (counts[0] >= 511) {
            return false; // ERROR
        }
        
        // Save off a copy of the counts
        // 1bc9f
        System.arraycopy(counts, 0, counts2, 0, counts2.length);
        
        v3 = 0;
        v4 = 15;
        while (true) {
            v5 = counts[v4] + v3;
            if ( (v5 & 1) != 0 ) {
                break;
            }
                
            v3 = v5 >> 1;
            --v4;
            if (v4 == 0) {
                if (v3 != 1) {
                    return false;
                }
                
                v7 = 0;
                v6 = 1;
                do {
                    counts[v6] += v7;
                    v7 = counts[v6++];
                } while (v6 < 16);
                
                if (DEBUG_TABLE_GEN) {  
                    System.out.printf("\n\n2: HEX digit counts:\n");
                    for( int index = 0; index < counts.length; index++) {
                        System.out.printf("\t0x%x = 0x%08X\n", index, counts[index]);
                    }
                }
                
                v9 = counts[15];
                total_counts = counts[15];
                v8 = 8192;
                
                do {
                    v10 = v8 - 16;
                    v11 = (source.get(v10 >> 5) >> 4) & 0x0f;
                    
                    if (v11 != 0) {
                        --counts[v11];
                        table[counts[v11]] = (short) (v11 |  v10);
                        v9 = total_counts;
                    }
                    v8 = v10 - 16;
                    v13 = (source.get(v8 >> 5) & 0xF);
                    if (v13 != 0) {
                        --counts[v13];
                        table[counts[v13]] = (short) (v13 | v8);
                    }
                } while (v8 != 0);
              
                         
                v15 = 2048;
                v14 = 2048;
                int xsource = 15;
                do {
                    for (i = v15; v14 > i; table[v15] = (short) (v14 | 0x8000)) {
                        v14 -= 2;
                        --v15;
                    }
                    for (j = counts2[xsource] - 1; j >= 0; table[v15] = v26 ) {
                        v26 = table[v9-- - 1];
                        --v15;
                        --j;
                    }
                    v14 = i;
                    v23 = (xsource - 1 < 10) ? 1 : 0;
                    v24 = (xsource == 11 ? 1 : 0);
                    v25 = ((xsource-- < 11) ? 1 : 0);
                } while (((v25 ^ v23) | v24) == 0);
                
                
                for (k = 1024; v14 > v15; table[k] = (short) (v14 | 0x8000)) {
                    v14 -= 2;
                    --k;
                }

                while (v9 > 0) {
                    if (DEBUG_TABLE_GEN) {                
                        System.out.printf("v9 = %d, k = %d\n", v9, k);
                    }
                    v20 = table[v9-- - 1];
                    if (DEBUG_TABLE_GEN) {                
                        System.out.printf("v20 = %d\n", v20);
                    }
                    v19 = k - (1024 >> (v20 & 0xF));
                    do {
                        --k;
                        table[k] = v20;
                        if (DEBUG_TABLE_GEN) {
                            System.out.printf("k=0x%x v19=0x%x\n", k, v19);
                        }
                    } while (k != v19);
                    
                }
                
                if (DEBUG_TABLE_GEN) {
                    System.out.printf("\n\n3: HEX digit table:\n");
                    for (int index = 0; index < 0x400; index++) {
                        System.out.printf("\t0x%x = 0x%04X\n", index, table[index] & 0xffff);
                    }
                }              
                
                return true;
            }
        }
        return false;
    }


    /* -- Here is where the magic happens -- */

    private static final int WORD_LENGTH = (8 * 4);
                                
    private static int ror(int value, int places) { 
        return (value>>places)|(value<<(WORD_LENGTH-places)); 
    } 

    static void decompress_hu01_block(ByteBuffer source_ptr, short[] table, ByteBuffer destination_ptr) throws HU01Exception {
        ByteBuffer source = source_ptr.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer destination = destination_ptr.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        
        // 1BA35
        int esi = (source.getShort() << 16) | (source.getShort() &  0xffff);
        int ch = 0,cl = 0;
        int eax = 0, edx = 0;
        int saved_esi = 0;
        int esi_ptr_pos = 0;
        int edi_ptr_pos = 0;
        
        ch = 0x10; // 1BA42
        
        int pc = 0x1BA44;
        loop: while (true) {
            switch (pc) {
            case 0x1BA44:
                if (source.hasRemaining() && destination.hasRemaining()) {
                    pc = 0x1BA56;
                    continue loop;
                } else {
                    // We're done
                    return;
                }
                
            case 0x1BA56:
                edx = esi; // 1BA56
                cl = 0x0f;
                edx = edx >>> 0x16;
                eax = table[edx & 0x3ff];    // 1BA5D
            
                // 1BA62
                if (eax<0) {
                    pc = 0x1BB55;
                    continue loop;
                }
                // 1BA6A
                cl = cl & (eax & 0xff);
                eax = eax >>> 4; 
                esi = esi << cl;
                ch = ch - cl;
                if (ch<0) {
                    pc = 0x1BAFA;
                    continue loop;
                }
                // 1BA79
                eax = eax - 0x100;
                if (eax<0) {
                    pc = 0x1BB2B;
                    continue loop;
                }
            
            case 0x1BA84:
                cl = eax & 0xff;
                edx = esi;
                cl = cl >>> 4;
                edx = edx | 1;
                esi = esi << cl;
                ch = ch - cl;
                // 1BA92
                edx = ror(edx, 1);
                cl = (cl ^ 0x1f) & 0xff;
                eax = eax & 0x0f;
                edx = edx >>> cl;
                // 1BA9C
                saved_esi = esi;
                edx = -edx;
                if (eax>5) {
                    pc = 0x1B9E1;
                    continue loop;
                }
                // 1BAA8
                esi_ptr_pos = destination.position() + edx;
                if (edx<=-3) {
                    pc = 0x36670;
                    continue loop;
                }
            
                if (esi_ptr_pos < destination_ptr.position()) {
                    throw new HU01Exception();
                }
            
                try {
                    for (int i = 0; i < eax + 3; i++) {
                        destination.put(destination.position() + i, destination.get(esi_ptr_pos + i));
                    }
                } catch (IndexOutOfBoundsException e) {
                    /*
                     * The original C code sometimes writes after the end of the destination buffer.
                     * C does no bounds checking so that code appears to work fine. However, in Java an 
                     * exception will be thrown.
                     */
                }
                if (DEBUG) {
                    try {
                        for (int i = 0; i < eax + 3; i++) {
                            System.out.printf("%c", (char) destination.get(destination.position() + i));                   
                        }
                    } catch (IndexOutOfBoundsException e) {
                    }
                }
            
                esi = saved_esi;
                destination.position(Math.min(destination.limit(), destination.position() + eax + 3));
            
                // 1BAC8
                if (ch>=0) {
                    pc = 0x1BA56;
                    continue loop;
                }
            
            case 0x1BAD0:
                if (!source.hasRemaining() || !destination.hasRemaining()) {
                    // We're done
                    return;
                }
                cl = ch;
                edx = source.getShort() & 0xffff;
                cl = -cl;
                edx = edx << cl;
                ch = ch + 0x10;
                esi = esi + edx;
                pc = 0x1BA56;
                continue loop;
            
            case 0x36670:
                if (esi_ptr_pos < destination_ptr.position()) {
                    throw new HU01Exception();
                }
            
                try {
                    for (int i = 0; i < eax + 3; i++) {
                        destination.put(destination.position() + i, destination.get(esi_ptr_pos + i));
                    }
                } catch (IndexOutOfBoundsException e) {
                    /*
                     * The original C code sometimes writes after the end of the destination buffer.
                     * C does no bounds checking so that code appears to work fine. However, in Java an 
                     * exception will be thrown.
                     */
                }
                if (DEBUG) {
                    try {
                        for (int i = 0; i < eax + 3; i++) {
                            System.out.printf("%c", (char) destination.get(destination.position()+i));                   
                        }
                    } catch (IndexOutOfBoundsException e) {
                    }
                }
            
                esi = saved_esi;
                destination.position(Math.min(destination.limit(), destination.position() + eax + 3));
                if (ch>=0) {
                    pc = 0x1BA56;
                    continue loop;
                }
            
                pc = 0x1BAD0;
                continue loop;
            
            case 0x1BAFA:
                if(!source.hasRemaining() || !destination.hasRemaining()) {
                    // We're done
                    return;
                }
                // 1BB0C
                cl = ch;
                edx = source.getShort() & 0xffff;
                cl = -cl;
                // 1BB15
                edx = edx << cl;
            case 0x1BB1A:
                ch = ch + 0x10;
                esi = esi + edx;
                eax = eax - 0x100;
                if (eax<0) {
                    pc = 0x1BB2B;
                    continue loop;
                }
            
                pc = 0x1BA84;
                continue loop;
            
            case 0x1BB2B:
                edx = esi;
            
                if (destination.hasRemaining()) {
                    /*
                     * Guard against buffer overflow.
                     * The original C code sometimes writes after the end of the destination buffer.
                     * C does no bounds checking so that code appears to work fine. However, in Java an 
                     * exception will be thrown.
                     */
                    if (DEBUG) {
                        System.out.printf("%c", (char) (eax & 0xff));
                    }
                    destination.put((byte) (eax & 0xff));    // decompressed byte written here
                }
                edx = edx >>> 0x16;
            
                eax = table[edx & 0x3ff];    // 1BB33
                cl = 0x0f;
            
                // 1BB3A
                if (eax<0) {
                    pc = 0x1BB55;
                    continue loop;
                }
            
                // 1BB3E
                cl = cl & (eax & 0xff);
                eax = eax >>> 4;
                esi = esi << cl;        
                // 1BB45
                ch = ch - cl;
                if (ch<0) {
                    pc = 0x1BAFA;
                    continue loop;
                }
                // 1BB49
                eax = eax - 0x100;
                if (eax<0) {
                    pc = 0x1BB2B;
                    continue loop;
                }
                pc = 0x1BA84;
                continue loop;
                
            case 0x1B9C5:
                if (!source.hasRemaining()) {
                    // We're done
                    return;
                }
                eax = source.get() & 0xff;
                // 1B9CC
                eax += 0x0f;
                if (eax!=0x10e) {
                    pc = 0x1B9E6;
                    continue loop;
                } else {
                    eax = source.getShort() & 0xffff;
                
                    if (eax>=0x10e) {
                        pc = 0x1B9E6;
                        continue loop;
                    }
                
                    throw new HU01Exception();
                }
            
            case 0x1B9E1:
                if (eax==0x0f) {
                    pc = 0x1B9C5;
                    continue loop;
                }
            
            case 0x1B9E6:
                // let's get ready to copy some bytes
                esi_ptr_pos = destination.position() + edx;
                eax = eax + 3;
                edi_ptr_pos = destination.position() + eax;
                if (esi_ptr_pos<0) {
                    throw new HU01Exception();
                }
            
//                if (edi_ptr_pos>=destination.limit()) {
//                    // We're done
//                    return;
//                }
            
                try {
                    for (int i = 0; i < eax; i++) {
                        destination.put(destination.position() + i, destination.get(esi_ptr_pos + i));
                    }
                } catch (IndexOutOfBoundsException e) {
                    /*
                     * The original C code sometimes writes after the end of the destination buffer.
                     * C does no bounds checking so that code appears to work fine. However, in Java an 
                     * exception will be thrown.
                     */
                }
                if (DEBUG) {
                    try {
                        for (int i = 0; i < eax; i++) {
                            System.out.printf("%c", (char) destination.get(destination.position()+i));                   
                        }
                    } catch (IndexOutOfBoundsException e) {
                    }
                }
                destination.position(Math.min(destination.limit(), edi_ptr_pos));
            
                esi = saved_esi;
                if (!destination.hasRemaining()) {
                    // We're done
                    return;
                }
                if (ch<0) {
                    pc = 0x1BAD0;
                    continue loop;
                }
                pc = 0x1BA56;
                continue loop;
            
            case 0x1BB55:
                // we got a negative decompression index
            
                esi = esi << 10;
                do {
                    eax = eax + (esi>>>31);
                    esi = esi + esi;
                    // 1BB5D
                    eax = table[0x8000+eax];
                } while (eax < 0);
            
                // 1BB69
                cl = cl & (eax & 0xff);
                eax = eax >>> 4;
                ch = ch - cl;
                if (ch < 0) {
                    pc = 0x1BAFA;
                    continue loop;
                }
                // 1BB72
                eax = eax - 0x100;
                if (eax >= 0) {
                    pc = 0x1BA84;
                    continue loop;
                }
            
                pc = 0x1BB2B;
                continue loop;
            }
        }
    }
    
}
