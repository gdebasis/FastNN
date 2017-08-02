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
import java.util.*;
import java.io.RandomAccessFile;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author Debasis
 */
class DocIdComparator implements Comparator<DocVector> {

    @Override
    public int compare(DocVector thisVec, DocVector thatVec) {
        int thisDocId = thisVec.getId();
        int thatDocId = thatVec.getId();
        return Integer.compare(thisDocId, thatDocId);  // ascending by integer docName
    }
}

public class IndexedVecQueries extends VecQueries {

    public IndexedVecQueries(String propFile) throws Exception {
        super(propFile);
    }

    @Override
    void loadQueries() throws Exception {

        int nnId = 0;
        float nnDist = 0;
        boolean eval = Boolean.parseBoolean(prop.getProperty("eval", "false"));
        
        File indexDir = new File(prop.getProperty("query.index"));
        IndexReader siftVecReader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));

        int numDocs = siftVecReader.numDocs();
        int numDimensions = Integer.parseInt(prop.getProperty("vec.numdimensions"));

        for (int i = 0; i < numDocs; i++) {
            Document d = siftVecReader.document(i);
            
            if (eval) {
                nnId = Integer.parseInt(d.get(QuerySiftVecIndexer.FIELD_NN_ID));
                nnDist = Float.parseFloat(d.get(QuerySiftVecIndexer.FIELD_NN_DIST));
            }

            qvecs.add(new QueryVector(d, numDimensions, DocVector.numIntervals, nnId, (float) Math.sqrt(nnDist)));
        }

        Collections.sort(qvecs, new DocIdComparator());
    }

    public static void main(String[] args) {
        try {
            IndexedVecQueries vecQueries = new IndexedVecQueries(args[0]); // prop file path
            List<DocVector> dvecs = vecQueries.getQueries();
            for (int i = 0; i < 2; i++) {
                System.out.println((QueryVector) dvecs.get(i));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
