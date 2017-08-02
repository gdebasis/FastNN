/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sift;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Debasis
 */
public class ByteRcd {
    int n;
    byte[] x;

    public ByteRcd(RandomAccessFile fp) throws Exception {        
        byte[] bytes = new byte[4];
        fp.read(bytes);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        n = byteBuffer.getInt();        
        
        bytes = new byte[n];
        fp.read(bytes);
        byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        
        x = new byte[n];
        for (int i=0; i < n; i++) {
            x[i] = byteBuffer.get(i);  // (i*4)
        }        
    }
    
    public static void main(String[] args) {

        RandomAccessFile fp;
        StringBuffer buff = new StringBuffer();
        
        try {
            fp = new RandomAccessFile("C:/research/RealValuedVecNN/dataset/queries.bvecs", "r");
            for (int k=0; k < 10000; k++) {
                ByteRcd br = new ByteRcd(fp);
                
                for (byte b : br.x) {
                    buff.append((int)(b & 0xFF)).append(",");
                }
                System.out.println(br.x.length);
                System.out.println(buff.toString());
                buff.setLength(0);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }        
}
