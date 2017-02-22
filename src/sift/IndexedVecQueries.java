/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sift;

import indexer.DocVector;
import indexer.QuerySiftVecIndexer;
import indexer.QueryVector;
import java.io.File;
import java.io.RandomAccessFile;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author Debasis
 */
public class IndexedVecQueries extends VecQueries {

    public IndexedVecQueries(String propFile) throws Exception {
        super(propFile);
    }
    
    @Override
    void loadQueries() throws Exception {
        
        File indexDir = new File(prop.getProperty("query.index"));
        IndexReader siftVecReader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
    
        int numDocs = siftVecReader.numDocs();
        int numDimensions = Integer.parseInt(prop.getProperty("vec.numdimensions"));
        
        for (int i=0; i < numDocs; i++) {
            Document d = siftVecReader.document(i);
            int nnId = Integer.parseInt(d.get(QuerySiftVecIndexer.FIELD_NN_ID));
            float nnDist = Float.parseFloat(d.get(QuerySiftVecIndexer.FIELD_NN_DIST));
            
            qvecs.add(new QueryVector(d, numDimensions, DocVector.numIntervals, nnId, nnDist));
        }        
    }
}
