/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package retriever;

import indexer.DocVector;
import indexer.QueryVector;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author Debasis
 */
public class SiftSubVecSearcher extends SiftVecSearcher {
    IndexReader[] readers;
    IndexSearcher[] searchers;
    int numSubSpaces;
    int subSpaceDimension;
    
    public SiftSubVecSearcher(String propFile) throws Exception {
        super(propFile);
        
        subSpaceDimension = Integer.parseInt(prop.getProperty("subspace.dimension"));        
        numSubSpaces = numDimensions/subSpaceDimension;
        assert(numSubSpaces*subSpaceDimension == numDimensions);
        
        readers = new IndexReader[numSubSpaces];
        searchers = new IndexSearcher[numSubSpaces];
        
        for (int i=0; i < numSubSpaces; i++) {
            File indexDir = new File(prop.getProperty("index") + "/" + i);        
            readers[i] = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
            searchers[i] = new IndexSearcher(readers[i]);
            searchers[i].setSimilarity(new LMJelinekMercerSimilarity(0.1f));
        }
        
    }
    
    List<DocVector> accumulateSubSpaceDistances(List<DocVector> subspaceDistances, List<DocVector> mergedSpaceDistances) {
        HashMap<Integer, DocVector> docIdMap = new HashMap<>();
        
        for (DocVector dvec : mergedSpaceDistances) {
            if (!docIdMap.containsKey(dvec.getId()))
                docIdMap.put(dvec.getId(), dvec);
        }
            
        // Check if the docid 'x' exists in the merged space vectors, if so,
        // update the distances by accumulating
        // mergedspace.distances[x] += subspace_dist[x]
        for (DocVector subspaceVec : subspaceDistances) {
            DocVector mergedSpaceVec = docIdMap.get(subspaceVec.getId());
            if (mergedSpaceVec == null)
                continue;
            mergedSpaceVec.accumulateDist(subspaceVec.getDistFromQuery());            
        }
        
        List<DocVector> newMergedDistances = new ArrayList<>();
        for (Map.Entry<Integer, DocVector> e : docIdMap.entrySet()) {
            newMergedDistances.add(e.getValue());
        }
        Collections.sort(newMergedDistances);        
        return newMergedDistances;
    }
    
    List<DocVector> searchQuery(DocVector qvec, int i) throws Exception {
        
        System.out.println("Executing Query: " + (i+1));
        List<DocVector> retrievedDocVecs = null;

        // Split up qvec into subvecs
        for (int j=0, k=0; j < numDimensions; j+=subSpaceDimension, k++) {
            DocVector qvecSub = qvec.getSubVector(j, subSpaceDimension);

            if (retrievedDocVecs == null)
                retrievedDocVecs = retrieveWithPivotedRelaxedQueries(searchers[k], qvecSub);
            else {
                List<DocVector> projectedSubspaceDistances = retrieveWithPivotedRelaxedQueries(searchers[k], qvecSub);
                retrievedDocVecs = accumulateSubSpaceDistances(retrievedDocVecs, projectedSubspaceDistances);
            }
        }
        return retrievedDocVecs;
    }
    
    @Override
    public void searchWithBenchmarkQueries() throws Exception {
        List<DocVector> queries = vecQueries.getQueries();
        int numQueries = 1; //queries.size();
        
        boolean eval = Boolean.parseBoolean(prop.getProperty("eval", "false"));
        
        int rAt1 = 0;
        
        for (int i=0; i < numQueries; i++) {
            long start = System.currentTimeMillis();
            DocVector qvec = queries.get(i);

            List<DocVector> retrievedDocVecs = searchQuery(qvec, i);
            
            long end = System.currentTimeMillis();
            System.out.println("Total execution time (s): " + (end-start)/1000);
            
            rAt1 += retrievedDocVecs.get(0).getId() == ((QueryVector)qvec).getNN()? 1 : 0;                        
        }
        
        if (eval) {
            System.out.println("R@1 = " + rAt1/numQueries);
        }

        // close the readers
        for (int i=0; i < numSubSpaces; i++) {
            readers[i].close();
        }
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java SiftSubvecSearcher <prop-file>");
            args[0] = "init.subvec.properties";
        }
        
        try {
            SiftSubVecSearcher searcher = new SiftSubVecSearcher(args[0]);
            searcher.searchWithBenchmarkQueries();
            searcher.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }    
}
