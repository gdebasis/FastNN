/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import java.nio.ByteBuffer;
import java.util.Properties;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author Debasis
 */

public class DocVector {
    String id;
    float[] x;
    Cell[] keys;
    
    public int numDimensions;
    public static int numIntervals;
    String quantized;
    
    static float MIN_VAL = 0;
    static float MAX_VAL = 1f;
    
    public static final String FIELD_ID = "docid";
    public static final String FIELD_CELL_ID = "cell";
    public static final String FIELD_VEC = "vec";
    
    static public void initVectorRange(Properties prop) {
        numIntervals = Integer.parseInt(prop.getProperty("vec.numintervals"));        
        MIN_VAL = Float.parseFloat(prop.getProperty("vec.min", "-1"));
        MAX_VAL = Float.parseFloat(prop.getProperty("vec.max", "1"));
    }
    
    public DocVector(String line, int numDimensions, int numIntervals) {
        String[] tokens = line.split("\\s+");
        this.id = tokens[0];        
        this.numDimensions = numDimensions;
        
        assert(tokens.length-1 == numDimensions);
        x = new float[numDimensions];
        
        StringBuffer buff = new StringBuffer();
        for (int i=1; i < tokens.length; i++) {
            x[i-1] = Float.parseFloat(tokens[i]);
            buff.append(tokens[i]).append(" ");
        }
        this.numIntervals = numIntervals;
        quantized = quantize();
    }
    
    // Generate random
    public DocVector(int numDimensions, int numIntervals) {
        this.numIntervals = numIntervals;
        this.numDimensions = numDimensions;
        StringBuffer buff = new StringBuffer();
        x = new float[numDimensions];
        for (int i=0; i < numDimensions; i++) {
            x[i] = MIN_VAL + (float)Math.random()*(MAX_VAL - MIN_VAL);
            buff.append(x[i]).append(" ");
        }        
        quantized = quantize();
    }
    
    public DocVector(float[] x, int numIntervals) {
        this.numDimensions = x.length;
        this.numIntervals = numIntervals;
        this.x = new float[numDimensions];
        StringBuffer buff = new StringBuffer();
        for (int i=0; i < numDimensions; i++) {
            this.x[i] = x[i];            
            buff.append(x[i]).append(" ");
        }
        quantized = quantize();
    }
    
    public DocVector(Document doc, int numDimensions, int numIntervals) {
        
        // Read the floating point number array from the index
        BytesRef bytesRef = doc.getBinaryValue(DocVector.FIELD_VEC);
        
        ByteBuffer buff = ByteBuffer.wrap(bytesRef.bytes);
        this.x = new float[numDimensions];
        for (int i=0; i < x.length; i++) {
            this.x[i] = buff.getFloat();
        }
        
        // Read the cell descriptors from the index
        String[] cellIds = doc.get(FIELD_CELL_ID).split("\\s+");
        keys = new Cell[numDimensions];
        for (int i=0; i < keys.length; i++) {
            keys[i] = new Cell(cellIds[i]);
        }
        
        this.numIntervals = numIntervals;
    }
    
    byte[] getVecBytes(float[] x) {
        ByteBuffer buff = ByteBuffer.allocate(Float.SIZE * x.length);
        for (float xval : x)
            buff.putFloat(xval);
        
        byte[] bytes = buff.array();        
        return bytes;
    }
    
    public String getQuantizedString() {
        return quantized;
    }
    
    public int getNumberofDimensions() { return this.numDimensions; }
    public int getNumberofIntervals() { return (int)this.numIntervals; }
    
    static public String getCellId(int dimension, int intervalOffset) {
        StringBuffer buff = new StringBuffer();
        buff
            .append(dimension)
            .append("_")    
            .append(intervalOffset);
        return buff.toString();
    }
    
    String quantize() {
        keys = new Cell[numDimensions];
        
        StringBuffer buff = new StringBuffer();
        
        for (int i=0; i < numDimensions; i++) {
            keys[i] = new Cell(i);
            keys[i] = keys[i].quantize(this);  // returns a new object...
            
            buff.append(keys[i].toString()).append(" ");
        }
        return buff.toString();
    }
    
    String quantize(Cell updatedCell) {
        StringBuffer buff = new StringBuffer();
        System.out.println("Quantization of cell: " + keys[updatedCell.axisId]);
        
        keys[updatedCell.axisId] = updatedCell; // e.g. replace 1_8 with 1_8_15 or 1_9_16 with 1_9_16_87 (another offset at a fine-grained level)
        
        System.out.println("Updated to: " + keys[updatedCell.axisId]);
        
        for (int i=0; i < keys.length; i++) {
            buff.append(keys[i]).append(" ");
        }
        
        buff.deleteCharAt(buff.length()-1);
        
        quantized = buff.toString();
        return buff.toString();        
    }
    
    Document constructDoc() {
        Document doc = new Document();
        
        doc.add(new Field(FIELD_ID, id==null?"":id, Field.Store.YES, Field.Index.NOT_ANALYZED));
        
        // Store the vectors as byte arrays (in binary format rather than text format
        // which takes more space...
        doc.add(new StoredField(FIELD_VEC, this.getVecBytes(x)));
        
        doc.add(new Field(FIELD_CELL_ID, quantized, Field.Store.YES, Field.Index.ANALYZED));
        
        return doc;
    }
    
    String getCoordinates() {
        StringBuffer buff = new StringBuffer("(");
        for (float x_i : x) {
            buff.append(x_i).append(",");
        }
        buff.deleteCharAt(buff.length()-1);
        buff.append(")");
        return buff.toString();
    }
    
    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff.append(getCoordinates()).append("\t").append(this.quantized);
        return buff.toString();
    }
    
    static DocVector[] synthesizeRandomly(int numSamples, int numDimensions, int numIntervals) {
        DocVector[] vecs = new DocVector[numSamples];
        for (int i = 0; i < numSamples; i++) {
            vecs[i] = new DocVector(numDimensions, numIntervals);
        }
        return vecs;
    }
    
    public static void main(String[] args) {
        DocVector[] dvecs = synthesizeRandomly(5, 2, 20000);
        for (DocVector dvec : dvecs) {
            System.out.println(dvec);
            dvec.getVecBytes(dvec.x);
        }
    }
}




