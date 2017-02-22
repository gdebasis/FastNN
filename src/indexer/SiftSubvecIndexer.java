/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import java.io.File;
import java.io.RandomAccessFile;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import sift.FloatByteRcd;

/**
 *
 * @author Debasis
 */
public class SiftSubvecIndexer extends SiftVecIndexer {
    int subSpaceDimension;
    int numsubVecs;
    IndexWriter[] subspaceWriters;
    
    public SiftSubvecIndexer(String propFile) throws Exception {
        super(propFile);

        subSpaceDimension = Integer.parseInt(prop.getProperty("subspace.dimension"));
        numsubVecs = numDimensions/subSpaceDimension;
        assert(numsubVecs*subSpaceDimension == numDimensions);
        
        subspaceWriters = new IndexWriter[numsubVecs];
        
        for (int i=0; i < numsubVecs; i++) {
            IndexWriterConfig iwcfg = new IndexWriterConfig(
                    new WhitespaceAnalyzer());
            iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            File indexDir = new File(this.indexPath + i);
            if (!indexDir.exists())
                indexDir.mkdir();
            subspaceWriters[i] = new IndexWriter(FSDirectory.open(indexDir.toPath()), iwcfg);        
        }
    }
    
    @Override
    void indexFile() throws Exception {
        File file = new File(prop.getProperty("dvec.file"));
        
        RandomAccessFile reader = new RandomAccessFile(file, "r");
        boolean normalize = Boolean.parseBoolean(prop.getProperty("normalize", "false"));
        
        final int batchSize = 10000;
        int count = 0;
        int i, j;

        FloatByteRcd fbr = null;
        do {
            fbr = FloatByteRcd.readNext(reader, count);
            if (fbr == null)
                break;
            
            DocVector wholeVec = fbr.getDocVec(normalize);
            for (j=0, i=0; j < numDimensions; j+=subSpaceDimension,i++) {
                DocVector dvec = fbr.getDocVec(normalize, i, j, subSpaceDimension);
                Document luceneDoc = dvec.constructDoc(wholeVec);

                subspaceWriters[i].addDocument(luceneDoc);
            }
            count++;
            if (count>batchSize && count%batchSize==0) {
                System.out.println("Added " + count + " vectors...");
                
            }
        }
        while (true);        
    }

    void closeAll() throws Exception {
        for (int i=0; i < numsubVecs; i++) {
            subspaceWriters[i].close();
        }
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java SiftVecIndexer <prop-file>");
            args[0] = "init.subvec.properties";
        }
        
        try {
            SiftSubvecIndexer siftIndexer = new SiftSubvecIndexer(args[0]);
            siftIndexer.processAll();
            siftIndexer.closeAll();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }    
    
}
