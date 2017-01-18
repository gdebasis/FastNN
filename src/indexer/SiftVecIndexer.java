/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import java.io.*;
import org.apache.lucene.document.Document;
import sift.FloatByteRcd;

/**
 *
 * @author Debasis
 */
public class SiftVecIndexer extends RealValuedVecIndexer {

    public SiftVecIndexer(String propFile, String indexDirName) throws Exception {
        super(propFile, indexDirName);
    }

    @Override
    void indexFile(File file) throws Exception {
        RandomAccessFile reader = new RandomAccessFile(file, "r");
        boolean normalize = Boolean.parseBoolean(prop.getProperty("normalize", "false"));
        
        final int batchSize = 100;
        int count = 0;

        FloatByteRcd fbr = null;
        do {
            fbr = FloatByteRcd.readNext(reader, count);
            if (fbr == null)
                break;
            
            DocVector dvec = fbr.getDocVec(normalize);
            Document luceneDoc = dvec.constructDoc();
                
            if (count%batchSize == 0) {
                System.out.println("Added " + count + " vectors...");
            }

            writer.addDocument(luceneDoc);
            count++;
        }
        while (true);        
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java SiftVecIndexer <prop-file>");
            args[0] = "init.properties";
        }
        
        try {
            SiftVecIndexer siftIndexer = new SiftVecIndexer(args[0], "index");
            siftIndexer.processAll();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }    
}
