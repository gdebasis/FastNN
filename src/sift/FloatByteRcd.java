/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sift;

import indexer.DocVector;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import rndvecgen.RandomVecGen;

/**
 *
 * @author Debasis
 */
public class FloatByteRcd {
    int id;
    int n;
    float[] x;
    
    static Random generator = new Random(123456);

    public FloatByteRcd(int id) {
        this.id = id;
    }
    
    public FloatByteRcd(int id, float[] x) {
        this.id = id;
        this.x = x;
    }
    
    public FloatByteRcd(int id, String line) {
        this.id = id;
        String[] tokens = line.split("\\s+");
        int i=0;
        x = new float[tokens.length];
        
        for (String token : tokens) {
            x[i++] = Float.parseFloat(token);
        }
    }
    
    public FloatByteRcd(String fileName) throws Exception {
        File file = new File(fileName);        
        RandomAccessFile reader = new RandomAccessFile(file, "r");
        
        this.x = new float[2];
        // Store min/max in this global object
        this.x[0] = Float.MAX_VALUE;  // min
        this.x[1] = Float.MIN_VALUE;  // max

        FloatByteRcd fbr = FloatByteRcd.readNext(reader, 0);
        while (fbr != null) { // read rcd at a time
            for (int i=0; i<fbr.x.length; i++) {
                if (fbr.x[i] < x[0]) { // x < min => min:= x
                    x[0] = fbr.x[i];
                }
                else if (fbr.x[i] > x[1]) { // x > max => max:=x
                    x[1] = fbr.x[i];
                }
            }            
            fbr = FloatByteRcd.readNext(reader, 0);
        }
        reader.close();
    }
    
    public float[] getVec() { return x; }
    
    public float getMin() {
        return x[0];
    }
    
    public float getMax() {
        return x[1];
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
    
    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (float x_i : this.x) {
            buff.append(x_i).append(" ");
        }
        return buff.toString();
    }
    
    public DocVector getDocVec(boolean normalize, float min, float max) {
        DocVector dvec;
        dvec = new DocVector(String.valueOf(id), x, DocVector.numIntervals, normalize, min, max);        
        //System.out.println(dvec.toString());
        return dvec;
    }
    
    // To get a sub-vector
    public DocVector getDocVec(boolean normalize, int id, int startDimension, int numDimensions, float min, float max) {
        float[] x = new float[numDimensions];
        System.arraycopy(this.x, startDimension, x, 0, numDimensions);
        DocVector dvec = new DocVector(String.valueOf(id), x, DocVector.numIntervals, normalize, min, max);        
        return dvec;
    }
    
    static public FloatByteRcd generateRandom(int id, int numDimensions, float min, float max) {        
        FloatByteRcd fbr = new FloatByteRcd(id);
        fbr.x = new float[numDimensions];
        
        for (int i=0; i < numDimensions; i++) {            
            fbr.x[i] = min + generator.nextFloat()*(max - min);
        }
        return fbr;
    }

    static public FloatByteRcd generateRandom(int id, RandomVecGen rvgen) {        
        FloatByteRcd fbr = new FloatByteRcd(id);
        int numDimensions = rvgen.getNumDimensions();
        fbr.x = new float[numDimensions];
        
        double[] sampled = rvgen.getMixDist().sample();
        for (int i=0; i < numDimensions; i++) {            
            fbr.x[i] = (float)sampled[i];
        }
        return fbr;
    }
    
    public static void main(String[] args) {

        FloatByteRcd fbr = null;
        RandomAccessFile fp;
        try {
            fp = new RandomAccessFile("C:/research/RealValuedVecNN/dataset/sift/sift/sift_base.fvecs", "r");

            do {
                fbr = FloatByteRcd.readNext(fp, 0);
                if (fbr == null)
                    break;
                System.out.println(fbr.toString());
            }
            while (true);        
        }
        catch (Exception ex) {
            Logger.getLogger(FloatByteRcd.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }        
}
