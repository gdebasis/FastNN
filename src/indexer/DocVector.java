/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import java.nio.ByteBuffer;
import java.util.Properties;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author Debasis
 */

public class DocVector implements Comparable<DocVector> {
    String id;
    float[] x;
    Cell[] keys;
    
    public int numDimensions;
    public static int numIntervals;
    String quantized;
    float distFromQry;
    
    static float MIN_VAL = 0;
    static float MAX_VAL = 1f;
    
    public static final String FIELD_ID = "docid";
    public static final String FIELD_CELL_ID = "cell";
    public static final String FIELD_VEC = "vec";
    
    public float getDistFromQuery() { return distFromQry; }
    
    static public void initVectorRange(Properties prop) {
        numIntervals = Integer.parseInt(prop.getProperty("vec.numintervals"));        
        MIN_VAL = Float.parseFloat(prop.getProperty("vec.min", "-1"));
        MAX_VAL = Float.parseFloat(prop.getProperty("vec.max", "1"));
    }

    public DocVector(DocVector that, int startDimension, int numDimensions) {
        this.id = that.id;
        this.numDimensions = numDimensions;
        this.keys = new Cell[numDimensions];
        this.x = new float[numDimensions];
        
        System.arraycopy(that.keys, startDimension, this.keys, 0, numDimensions);
        System.arraycopy(that.x, startDimension, this.x, 0, numDimensions);
        
        quantized = quantize();
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
        DocVector.numIntervals = numIntervals;
        quantized = quantize();
    }
    
    // Generate random
    public DocVector(int id, int numDimensions, int numIntervals, SplitCells splitCells) {
        this.id = String.valueOf(id);
        DocVector.numIntervals = numIntervals;
        this.numDimensions = numDimensions;
        StringBuffer buff = new StringBuffer();
        x = new float[numDimensions];
        for (int i=0; i < numDimensions; i++) {
            x[i] = MIN_VAL + (float)Math.random()*(MAX_VAL - MIN_VAL);
            buff.append(x[i]).append(" ");
        }
        if (splitCells == null)
            quantized = quantize();
        else
            quantized = quantizeWithSplitCells(splitCells);            
    }
    
    public Cell[] getCells() {
        return keys;
    }
    
    public int getId() { return Integer.parseInt(id); }
    
    public DocVector(String id, float[] x, int numIntervals, boolean normalize) {
        this.id = id;
        this.numDimensions = x.length;
        DocVector.numIntervals = numIntervals;
        
        this.x = new float[numDimensions];
        for (int i=0; i < numDimensions; i++) {
            this.x[i] = x[i];
        }
        normalize();
        
        StringBuffer buff = new StringBuffer();
        for (int i=0; i < numDimensions; i++) {
            buff.append(this.x[i]).append(" ");
        }
        quantized = quantize();
    }
    
    final void normalize() {  // in [0, 1]
        float max = Float.MIN_VALUE, min = Float.MAX_VALUE;
        for (int i=0; i < x.length; i++) {
            if (x[i] > max)
                max = x[i];            
            else if (x[i] < min)
                min = x[i];
        }
        
        float z = max - min;
        for (int i=0; i < x.length; i++) {
            x[i] = (x[i] - min)/z;
        }    
    }
    
    public DocVector(Document doc, int numDimensions, int numIntervals, SplitCells splitCells) {
        
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
        
        this.numDimensions = numDimensions;
        DocVector.numIntervals = numIntervals;
        this.id = doc.get(DocVector.FIELD_ID);
                
        if (splitCells == null)
            quantized = quantize();
        else
            quantized = quantizeWithSplitCells(splitCells);            
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
        
        this.numDimensions = numDimensions;
        DocVector.numIntervals = numIntervals;
        this.id = doc.get(DocVector.FIELD_ID);                
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
    
    // for ranking purpose
    public void setDistWithQry(float sim) { this.distFromQry = sim; }
    
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
    
    public Document constructDoc() {
        Document doc = new Document();
        
        doc.add(new Field(FIELD_ID, id==null?"":id, Field.Store.YES, Field.Index.NOT_ANALYZED));
        
        // Store the vectors as byte arrays (in binary format rather than text format
        // which takes more space...
        doc.add(new StoredField(FIELD_VEC, this.getVecBytes(x)));
        
        doc.add(new Field(FIELD_CELL_ID, quantized, Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
        
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
            vecs[i] = new DocVector(i, numDimensions, numIntervals, null);
        }
        return vecs;
    }
    
    void setQuantized(String quantized) {
        this.quantized = quantized;
    }
    
    String quantizeWithSplitCells(SplitCells splitCells) {
        StringBuffer buff = new StringBuffer();        
        for (int i=0; i < this.numDimensions; i++) {
            buff.append(Cell.constructQuantizedQueryCell(this, i, splitCells).toString()).append(" ");
        }
        return buff.toString();
    }
    
    public void addNoise(float epsilon) {
        for (int i=0; i < numDimensions; i++) {
            this.x[i] += (-epsilon + Math.random()*epsilon);
        }
    }

    double[] toDoubleArray(float[] arr) {
        if (arr == null) return null;
        int n = arr.length;
        double[] ret = new double[n];
        for (int i = 0; i < n; i++) {
          ret[i] = arr[i];
        }
        return ret;
    }
    
    public float getDist(DocVector that) {
        /*
        EuclideanDistance dist = new EuclideanDistance();        
        return (float)dist.compute(toDoubleArray(x), toDoubleArray(that.x));
        */
        
        float dist = 0, del = 0;
        for (int i=0; i < numDimensions; i++) {
            del = x[i] - that.x[i];
            dist += del*del;
        }
        return dist;
    }

    @Override
    public int compareTo(DocVector that) {
        return Float.compare(this.distFromQry, that.distFromQry);
    }
    
    @Override
    public boolean equals(Object o) {
        DocVector that = (DocVector)o;
        return this.id.equals(that.id);
    }

    public DocVector getSubVector(int startDimension, int numDimensions) {
        DocVector subvec = new DocVector(this, startDimension, numDimensions);
        return subvec;
    }
    
    public static void main(String[] args) {
        DocVector[] dvecs = synthesizeRandomly(5, 2, 20000);
        for (DocVector dvec : dvecs) {
            System.out.println(dvec);
            dvec.getVecBytes(dvec.x);
        }
    }
}




