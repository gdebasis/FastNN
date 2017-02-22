/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import java.io.File;
import java.io.RandomAccessFile;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;
import sift.FloatByteRcd;

/**
 *
 * @author Debasis
 */

class NNInfo {
    int nnId;
    float dist;

    public NNInfo(int nnId, float dist) {
        this.nnId = nnId;
        this.dist = dist;
    }
    
    @Override
    public String toString() {
        return nnId + " (" + dist + ")";
    }
}

public class QuerySiftVecIndexer extends SiftVecIndexer {
    
    IndexReader siftVecReader;
    public static final String FIELD_NN_ID = "nnid";    
    public static final String FIELD_NN_DIST = "nndist";
    
    public QuerySiftVecIndexer(String propFile, String indexDirName) throws Exception {
        super(propFile, indexDirName);
    
        // Open the index where the doc vectors are stored
        File indexDir = new File(prop.getProperty("optimized.index"));
        siftVecReader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
    }
    
    @Override
    void indexFile() throws Exception {
        File file = new File(prop.getProperty("queryvecs.file"));
        
        RandomAccessFile reader = new RandomAccessFile(file, "r");
        boolean normalize = Boolean.parseBoolean(prop.getProperty("normalize", "false"));
        
        FloatByteRcd fbr = null;
        int count = 0;
        do {
            fbr = FloatByteRcd.readNext(reader, count);
            if (fbr == null)
                break;
            
            DocVector dvec = fbr.getDocVec(normalize);
            Document luceneDoc = dvec.constructDoc();
            
            NNInfo nnInfo = getNN(dvec);
            luceneDoc.add(new Field(FIELD_NN_ID, String.valueOf(nnInfo.nnId), Field.Store.YES, Field.Index.NOT_ANALYZED));
            luceneDoc.add(new Field(FIELD_NN_DIST, String.valueOf(nnInfo.dist), Field.Store.YES, Field.Index.NOT_ANALYZED));
                
            writer.addDocument(luceneDoc);
            System.out.println("Saved query vec: " + count + "(NN = " + nnInfo + ")");
            count++;
        }
        while (true);        
        //while (count < 2);
    }
    
    void close() throws Exception {
        siftVecReader.close();
    }
    
    // Do an exhaustive distance computation across all docs in the index
    // to find the nearest neighbor and save it in the query index.
    NNInfo getNN(DocVector qvec) throws Exception {
        int numDocs = siftVecReader.numDocs();
        int numDimensions = qvec.getNumberofDimensions();
        int numIntervals = qvec.getNumberofIntervals();
        
        float minDist = Float.MAX_VALUE;
        int minDocId = 0;
        DocVector minDocVector = null;
        float dist;
        
        for (int i=0; i < numDocs; i++) {
            Document doc = siftVecReader.document(i);
            DocVector dvec = new DocVector(doc, numDimensions, numIntervals);
            
            dist = dvec.getDist(qvec);
            if (dist < minDist) {
                minDist = dist;
                minDocId = i;
                minDocVector = dvec;
            }
        }
        
        //System.out.println(minDocVector);
        //System.out.println(qvec);
        
        return new NNInfo(minDocId, minDist);
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java SiftVecIndexer <prop-file>");
            args[0] = "init.properties";
        }
        
        try {
            QuerySiftVecIndexer siftIndexer = new QuerySiftVecIndexer(args[0], "query.index");
            siftIndexer.processAll();
            siftIndexer.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }    
}
