/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import org.apache.lucene.document.Document;

/**
 *
 * @author Debasis
 */
public class QueryVector extends DocVector {
    int nnId;
    float nnDist;
    
    public QueryVector(Document doc, int numDimensions, int numIntervals, int nnId, float nnDist) {
        super(doc, numDimensions, numIntervals);
        this.nnId = nnId;
        this.nnDist = nnDist;
    }
    
    public int getNN() { return nnId; }
    public float getnnDist() { return nnDist; }
    
}
