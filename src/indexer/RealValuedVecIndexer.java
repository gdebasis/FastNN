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
import rndvecgen.RandomVecGen;

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
    RandomVecGen rvgen;
    
    public RealValuedVecIndexer(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));
        
        numDimensions = Integer.parseInt(prop.getProperty("vec.numdimensions"));
        DocVector.initVectorRange(prop);
        numIntervals = DocVector.numIntervals;
        
        boolean syntheticQueries = prop.getProperty("data.source").equals("synthetic");
        if (syntheticQueries) {
            rvgen = new RandomVecGen(prop);        
            indexPath = rvgen.randomSamplesFileName() + ".index";
        }
        else
            indexPath = prop.getProperty("index");        
        
    }
    
    public RealValuedVecIndexer(String propFile, String indexDirName) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));        
        
        numDimensions = Integer.parseInt(prop.getProperty("vec.numdimensions"));        
        DocVector.initVectorRange(prop);
        numIntervals = DocVector.numIntervals;
        
        boolean syntheticQueries = prop.getProperty("data.source").equals("synthetic");
        if (syntheticQueries) {
            rvgen = new RandomVecGen(prop);        
            indexPath = rvgen.randomSamplesFileName() + ".index";
        }
        else
            indexPath = prop.getProperty(indexDirName);        
        
        IndexWriterConfig iwcfg = new IndexWriterConfig(
                new WhitespaceAnalyzer());
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        writer = new IndexWriter(FSDirectory.open(new File(indexPath).toPath()), iwcfg);        
    }
    
    public void processAll() throws Exception {
        System.out.println("Indexing Real-valued vectors...");
        
        indexAll();        

        if (writer != null)
            writer.close();
    }
    
    void indexAll() throws Exception {        
        String dataSource = prop.getProperty("data.source");
        if (dataSource.equals("external"))
            indexFile();       
        else {
            indexRandom();
            indexFile(rvgen.randomSamplesFileName());
        }
    }
    
    void indexRandom() throws Exception {        
        boolean generate = Boolean.parseBoolean(prop.getProperty("syntheticdata.generate", "false"));
        if (generate)
            rvgen.generateSamples();                        
    }
    
    void indexFile(String fileName) throws Exception {
        
        FileReader fr = new FileReader(fileName);
        BufferedReader br = new BufferedReader(fr);
        String line;
        
        final int batchSize = 10000;
        int count = 0;
        // Each line is a tweet document
        while ((line = br.readLine()) != null) {
            DocVector dvec = new DocVector(line, count, numDimensions, numIntervals);
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
    
    void indexFile() throws Exception {
        indexFile(prop.getProperty("dvec.file"));
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java RealValuedVecIndexer <prop-file>");
            args[0] = "init_synthetic.properties";
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
