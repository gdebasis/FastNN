/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sift;

import indexer.DocVector;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 *
 * @author Debasis
 */
public class FloatByteRcd {
    int id;
    int n;
    float[] x;

    public FloatByteRcd(int id) {
        this.id = id;
    }
    
    public static FloatByteRcd readNext(RandomAccessFile fp, int id) throws Exception {
        FloatByteRcd fbr = new FloatByteRcd(id);
        
        byte[] bytes = new byte[4];
        int numRead = fp.read(bytes);
        if (numRead < 4)
            return null;
        
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        fbr.n = byteBuffer.getInt();
        
        bytes = new byte[fbr.n<<2];
        numRead = fp.read(bytes);
        if (numRead < (fbr.n<<2))
            return null;
        
        byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        
        fbr.x = new float[fbr.n];
        for (int i=0; i < fbr.n; i++) {
            fbr.x[i] = byteBuffer.getFloat((i<<2));  // (i*4)
        }        
        
        return fbr;
    }
    
    public FloatByteRcd(RandomAccessFile fp, int id) throws Exception {
        this.id = id;
        
        byte[] bytes = new byte[4];
        fp.read(bytes);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        n = byteBuffer.getInt();
        
        bytes = new byte[4*n];
        fp.read(bytes);
        byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        
        x = new float[n];
        for (int i=0; i < n; i++) {
            x[i] = byteBuffer.getFloat((i<<2));  // (i*4)
        }        
    }

    public DocVector getDocVec(boolean normalize) {
        DocVector dvec = new DocVector(String.valueOf(id), x, DocVector.numIntervals, normalize);        
        return dvec;
    }
    
    // To get a sub-vector
    public DocVector getDocVec(boolean normalize, int id, int startDimension, int numDimensions) {
        float[] x = new float[numDimensions];
        System.arraycopy(this.x, startDimension, x, 0, numDimensions);
        DocVector dvec = new DocVector(String.valueOf(id), x, DocVector.numIntervals, normalize);        
        return dvec;
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (float x_i : this.x) {
            buff.append(x_i).append("   ");
        }
        return buff.toString();
    }

    public static void main(String[] args) {

        FloatByteRcd fbr = null;
        RandomAccessFile fp;
		if (args.length < 1) {
			System.err.println("Usage: java FloatByteRcd <file>");
			return;
		}

        try {
            fp = new RandomAccessFile(args[0], "r");

            do {
                fbr = FloatByteRcd.readNext(fp, 0);
                if (fbr == null)
                    break;
                System.out.println(fbr);
            }
            while (true);        
        }
        catch (Exception ex) {
			ex.printStackTrace();
        }
        
    }
}
