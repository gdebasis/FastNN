/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package retriever;

import indexer.DocVector;
import java.util.List;
import org.apache.lucene.search.TopDocs;
import sift.VecQueries;

/**
 *
 * @author Debasis
 */
public class SiftVecSearcher extends QuantizedVecSearcher {
    VecQueries vecQueries;
    
    SiftVecSearcher(String propFile) throws Exception {
        super(propFile);
        vecQueries = new VecQueries(propFile);
    }
    
    public void searchWithBenchmarkQueries() throws Exception {
        List<DocVector> queries = vecQueries.getQueries();
        int numQueries = 5; // queries.size();
        
        for (int i=0; i < numQueries; i++) {
            DocVector qvec = queries.get(i);
            System.out.println("Executing Query: " + i);
            
            //TopDocs topDocs = retrieveWithIncrementalQuery(qvec);
            //TopDocs topDocs = retrieveWithRelaxedQuery(qvec);
            //System.out.println("#retrieved: " + topDocs.scoreDocs.length);
            
            //List<DocVector> retrievedDocVecs = rerankByEuclideanDist(qvec, topDocs);
            List<DocVector> retrievedDocVecs = retrieveWithPivotedRelaxedQueries(qvec);
            vecQueries.evaluate(reader, i, retrievedDocVecs);
        }
        
        System.out.println("R@1 = " + vecQueries.rAt[0]/numQueries);
        System.out.println("R@10 = " + vecQueries.rAt[1]/numQueries);
        System.out.println("R@100 = " + vecQueries.rAt[2]/numQueries);        
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java QuantizedVecSearcher <prop-file>");
            args[0] = "init.properties";
        }
        
        try {
            SiftVecSearcher searcher = new SiftVecSearcher(args[0]);
            searcher.searchWithBenchmarkQueries();
            searcher.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
    
}
