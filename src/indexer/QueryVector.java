/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import org.apache.commons.math3.distribution.MixtureMultivariateNormalDistribution;
import org.apache.lucene.document.Document;
import rndvecgen.RandomVecGen;

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

    public QueryVector(int id, int numDimensions) {
        super(id, numDimensions, DocVector.numIntervals, null);
    }

    public QueryVector(MixtureMultivariateNormalDistribution dist, int id) {
        super(dist, id);
    }
    
    public QueryVector(String line, int numDimensions, int numIntervals, int nnId, float nnDist) {
        super(line, numDimensions, numIntervals);
        this.nnId = nnId;
        this.nnDist = nnDist;
    }
    
    public String toString() {
        return super.toString() + ", nnid: " + nnId + ", nndist: " + nnDist;
    }

    public int getNN() {
        return nnId;
    }

    public float getNNDist() {
        return nnDist;
    }

    public void setNNInfo(int nnId, float nnDist) {
        this.nnId = nnId;
        this.nnDist = nnDist;
    }
}
