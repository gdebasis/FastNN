/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Properties;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * Quantizes a real-valued normalized vector, where each x_i \in [0, 1].
 * Divide each x_i into "numIntervals" intervals, as a result of which
 * we create numIntervals^numDimensions cells, e.g. if we divide
 * each side of a square in 10 intervals, we get 100 cells.
 * Each cell (a discrete point hyper-cube in n dimensional space) has
 * a distinct name, cell_[<interval no.>]+, e.g. cell_2_3 for a square cell.
 * 
 * @author Debasis
 */

public class RealValuedVecIndexer {
    int numDimensions;
    int numIntervals;
    Properties prop;
    IndexWriter writer;
    String indexPath;
    
    public RealValuedVecIndexer(String propFile, String indexDirName) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));        
        indexPath = prop.getProperty(indexDirName);        
        
        numDimensions = Integer.parseInt(prop.getProperty("vec.numdimensions"));
        
        DocVector.initVectorRange(prop);
        numIntervals = DocVector.numIntervals;
        
        IndexWriterConfig iwcfg = new IndexWriterConfig(
                new WhitespaceAnalyzer());
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        writer = new IndexWriter(FSDirectory.open(new File(indexPath).toPath()), iwcfg);        
    }
    
    void processAll() throws Exception {
        System.out.println("Indexing Real-valued vectors...");
        
        indexAll();        
        
        writer.close();
    }
    
    void indexAll() throws Exception {
        if (writer == null) {
            System.err.println("Skipping indexing... Index already exists at " + indexPath + "!!");
            return;
        }
        
        File docFile = new File(prop.getProperty("dvec.file"));
        String dataSource = prop.getProperty("data.source");
        if (dataSource.equals("external"))
            indexFile(docFile);       
        else
            indexRandom();
    }
    
    void indexRandom() throws Exception {
        
        final int batchSize = 10000;
        int count = 0, totalCount = 0;
        int numRandomSamples = Integer.parseInt(prop.getProperty("syntheticdata.numsamples", "100000"));
        
        for (int i = 0; i < numRandomSamples; i++) {
            DocVector dvec = new DocVector(i, numDimensions, numIntervals, null);
            System.out.println(dvec.toString());
            Document luceneDoc = dvec.constructDoc();
            writer.addDocument(luceneDoc);
            if (count == batchSize) {
                System.out.println("Added " + totalCount + " vectors...");
                count = 0;           
            }
            count++;
            totalCount++;
        }
    }
    
    void indexFile(File file) throws Exception {
        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);
        String line;
        
        final int batchSize = 10000;
        int count = 0;
        // Each line is a tweet document
        while ((line = br.readLine()) != null) {
            DocVector dvec = new DocVector(line, numDimensions, numIntervals);
            Document luceneDoc = dvec.constructDoc();
            
            if (count%batchSize == 0) {
                System.out.println("Added " + count + " vectors...");
            }
            
            writer.addDocument(luceneDoc);
            count++;
        }
        br.close();
        fr.close();        
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java RealValuedVecIndexer <prop-file>");
            args[0] = "init.properties";
        }
        
        try {
            RealValuedVecIndexer rvIndexer = new RealValuedVecIndexer(args[0], "index");
            rvIndexer.processAll();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }    
}
