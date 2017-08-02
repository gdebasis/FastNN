/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package approxnn;

import indexer.DocVector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

/**
 *
 * @author Debasis
 */

class DocIdSim implements Comparable<DocIdSim > {
    ScoreDoc sd;

    public DocIdSim(ScoreDoc sd) {
        this.sd = sd;
    }

    @Override
    public int compareTo(DocIdSim that) { // descending by similarity
        return -1*Float.compare(sd.score, that.sd.score);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DocIdSim other = (DocIdSim) obj;
        return this.sd.doc == other.sd.doc;
    }
    
    @Override
    public int hashCode() {
        return Integer.hashCode(this.sd.doc);
    }    
}

public class ANNList {
    Set<DocIdSim> neighbors;

    public ANNList(TopDocs topdocs) {
        neighbors = new HashSet<>();
        for (ScoreDoc sd : topdocs.scoreDocs) {
            neighbors.add(new DocIdSim(sd));
        }
    }
    
    public ANNList(Set<DocIdSim> neighbors) {
        this.neighbors = neighbors;
    }
    
    public ANNList(DocVector vec) {
        this.neighbors = new HashSet<>();
        ScoreDoc sd = new ScoreDoc(vec.getId(), 0);
        this.neighbors.add(new DocIdSim(sd));
    }
    
    public ANNList(List<DocVector> vecs) {
        this.neighbors = new HashSet<>();
        for (DocVector vec : vecs) {
            ScoreDoc sd = new ScoreDoc(vec.getId(), 0);
            this.neighbors.add(new DocIdSim(sd));
        }
    }
    
 
    static public ANNList getIntersection(ANNList a, ANNList b) {
        HashSet<DocIdSim> x = new HashSet<>(a.neighbors);
        x.retainAll(b.neighbors);
        return new ANNList(x);
    }

    static public ANNList getUnion(ANNList a, ANNList b) {
        HashSet<DocIdSim> x = new HashSet<>(a.neighbors);
        x.addAll(b.neighbors);
        return new ANNList(x);
    }

    public List<DocVector> selectTopK(DocVector qvec, IndexReader reader, int k) throws Exception {

        PriorityQueue<DocVector> nearest = new PriorityQueue();
        
        for (DocIdSim docIdSim : this.neighbors) {
            Document d = reader.document(docIdSim.sd.doc);
            DocVector dvec = new DocVector(d, qvec.numDimensions, DocVector.numIntervals);
            float dist = qvec.getDist(dvec);
            dvec.setDistWithQry(dist);            
            nearest.add(dvec);
        }

        List<DocVector> topDocsList = new ArrayList<>();
        k = Math.min(k, nearest.size());
        for (int i=0; i < k; i++) {
            topDocsList.add(nearest.poll());
        }
        
        return topDocsList;
    }

    // Use similarity scores
    public Set<DocIdSim> selectTopKSim(DocVector qvec, IndexReader reader, int k) throws Exception {
        
        // Resort the set of doc-score similarity objects and select top k
        List<DocIdSim> sortedDocIdSims = new ArrayList<>(this.neighbors.size());
        for (DocIdSim dIdSim : this.neighbors) {
            sortedDocIdSims.add(dIdSim);
        }
        Collections.sort(sortedDocIdSims);

        k = Math.min(k, sortedDocIdSims.size());
        Set<DocIdSim> sortedSet = new HashSet<>(k);
        
        for (int i=0; i < k; i++) {
            sortedSet.add(sortedDocIdSims.get(i));
        }        
        return sortedSet;
    }
    
    public List<DocVector> selectTop(DocVector qvec, IndexReader reader) throws Exception {
        float minDist = Float.MAX_VALUE;
        DocVector ann = null;
        
        for (DocIdSim docIdSim : this.neighbors) {
            Document d = reader.document(docIdSim.sd.doc);
            DocVector dvec = new DocVector(d, qvec.numDimensions, DocVector.numIntervals, true);
            float dist = qvec.getDist(dvec);
            dvec.setDistWithQry(dist);            
            if (dist < minDist) {
                minDist = dist;
                ann = dvec;
            }
        }
        
        List<DocVector> topList = new ArrayList<>(1);
        topList.add(ann);
        return topList;
    }
}
