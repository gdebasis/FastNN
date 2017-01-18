/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sift;

import indexer.DocVector;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

/**
 *
 * @author Debasis
 */
public class Qrels {
    int numQueries;
    List<int[]> relVecIds;
    
    public Qrels(int numQueries) {
        this.numQueries = numQueries;
        relVecIds = new ArrayList<>();
    }
    
    void load(String fileName) throws Exception {
        System.out.println("Loading " + numQueries + " qrels records...");
        RandomAccessFile reader = new RandomAccessFile(fileName, "r");        
        for (int k=0; k < numQueries; k++) {
            IntByteRcd ibr = new IntByteRcd(reader);
            relVecIds.add(ibr.x);
        }
        // sanity check to ensure that we've reached EOF
        byte[] bytes = new byte[1];
        int n = reader.read(bytes);
        assert(n <= 0);        
    }
    
    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        int j=0;
        
        for (int[] topk : relVecIds) {
            buff.append(j++).append(": ");
            for (int i=0; i<topk.length; i++) {
                buff.append(topk[i]).append(",");
            }
            buff.append("\n");
        }
        return buff.toString();
    }
    
    String getDocIdsAsString(int []docIds) {
        StringBuffer buff = new StringBuffer();
        for (int docId : docIds)
            buff.append(docId).append(",");
        
        if (buff.length() > 1)
            buff.deleteCharAt(buff.length()-1);
        return buff.toString();
    }    
    
    public List<DocVector> getSortedRelVecs(IndexReader reader, DocVector qvec, int qid) throws Exception {
        List<DocVector> relDocs = new ArrayList<>();
        int[] relDocIds = this.relVecIds.get(qid);
        
        for (int id : relDocIds) {
            DocVector nnVec = new DocVector(reader.document(id), qvec.numDimensions, DocVector.numIntervals);
            nnVec.setDistWithQry(qvec.getDist(nnVec));
            relDocs.add(nnVec);            
        }
        Collections.sort(relDocs);        
        return relDocs;
    }
}


