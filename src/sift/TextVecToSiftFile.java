/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sift;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Debasis
 */
public class TextVecToSiftFile {
    String inFile;
    String outFile;
    
    public TextVecToSiftFile(String inFile, String outFile) throws Exception {
        this.inFile = inFile;
        this.outFile = outFile;
    }
    
    public TextVecToSiftFile(String outFile) throws Exception {
        this.outFile = outFile;
    }
    
    public float[] strToVec(String line) {
        String[] tokens = line.split("\\s+");
        int ndimensions = tokens.length-1;
        float[] vec = new float[ndimensions];
        for (int i=0; i < vec.length; i++)
            vec[i] = Float.parseFloat(tokens[i+1]);
        return vec;
    }
    
    void convert() throws Exception {
        FileReader fr = new FileReader(inFile);
        BufferedReader br = new BufferedReader(fr);
        RandomAccessFile ow = new RandomAccessFile(outFile, "rw");
        String line;
        
        while ((line = br.readLine()) != null) {
            float[] x = strToVec(line);
            writeRcd(ow, x);
        }
        br.close();
        fr.close();
        ow.close();
    }
    
    void writeRcd(RandomAccessFile file, float[] x) throws Exception {
        int len = 4 + (x.length<<2);
        ByteBuffer buffer = ByteBuffer.allocate(len);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        buffer.putInt(x.length);
        for (int i=0; i< x.length; i++)
            buffer.putFloat(x[i]);
        
        file.write(buffer.array());
    }
    
    public static void main(String[] args) {
        // Read a fvecs file in text and write back in 
        
        final String VEC_FILE = "C:/research/RealValuedVecNN/dataset/siftsmall/siftsmall/siftsmall_query.fvecs";
        final String OUT_VEC_FILE = "C:/research/RealValuedVecNN/dataset/siftsmall/siftsmall/siftsmall_query_converted.fvecs";
        
        try {
            FloatByteRcd fbr = null;
            RandomAccessFile fp, fpout;
            
            fp = new RandomAccessFile(VEC_FILE, "r");
            fpout = new RandomAccessFile(OUT_VEC_FILE, "rw");
            
            TextVecToSiftFile converter = new TextVecToSiftFile(OUT_VEC_FILE);

            do {
                fbr = FloatByteRcd.readNext(fp, 0);
                if (fbr == null)
                    break;
                
                System.out.println(fbr.toString());
                converter.writeRcd(fpout, fbr.x);
            }
            while (true); 
            
            fp.close();
            fpout.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
}
