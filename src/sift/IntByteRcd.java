/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sift;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 *
 * @author Debasis
 */
public class IntByteRcd {
    int n;
    int[] x;

    public IntByteRcd(RandomAccessFile fp) throws Exception {        
        byte[] bytes = new byte[4];
        fp.read(bytes);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        n = byteBuffer.getInt();        
        
        bytes = new byte[4*n];
        fp.read(bytes);
        byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        
        x = new int[n];
        for (int i=0; i < n; i++) {
            x[i] = byteBuffer.getInt((i<<2));  // (i*4)
        }        
    }
}


