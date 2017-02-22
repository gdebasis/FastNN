/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package approxnn;

import indexer.Cell;
import indexer.DocVector;
import indexer.QueryVector;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import retriever.SiftVecSearcher;
import sift.IndexedVecQueries;
import sift.VecQueries;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.store.MMapDirectory;

/**
 *
 * @author Debasis
 */
public class ANNRetriever {
    Properties prop;
    IndexReader reader;  // the combined index to search
    IndexSearcher searcher;
    int numDimensions;
    int numIntervals;
    VecQueries indexedVecQueries;
    VecQueries vecQueries;
    boolean debug;
    
    public ANNRetriever(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));                
        numDimensions = Integer.parseInt(prop.getProperty("vec.numdimensions"));
        
        // Read from optimized index (instead of the initial index)
        String indexPath = prop.getProperty("index");
        if (indexPath != null) {            
            File indexDir = new File(indexPath);

            //reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
            reader = DirectoryReader.open(MMapDirectory.open(indexDir.toPath()));
            //reader = DirectoryReader.open(new RAMDirectory(FSDirectory.open(indexDir.toPath()), IOContext.DEFAULT));
            
            searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new LMJelinekMercerSimilarity(0.1f)); // almost close to tf        
        }
        DocVector.initVectorRange(prop);
        numIntervals = DocVector.numIntervals;                
        
        indexedVecQueries = new IndexedVecQueries(propFile);
        //vecQueries = new VecQueries(propFile);
        
        debug = Boolean.parseBoolean(prop.getProperty("debug", "false"));
    }
    
    void close() throws Exception {
        reader.close();
    }
    
    // For every dimension of the query vector,
    // compute the set of vectors within the epsilon neighborhood
    // of the projected vectors in that dimension.
    // Call this set N(epsilon)
    // Will need to merge N(0) union N(1) union ... N(max)
    // Note that these sets are mutually disjoint
    ANNList retrieve(DocVector qvec, int epsilon) throws Exception {
        Query subspaceQuery;
        TopDocs retrDocs = null;
        
        Cell[] cells = qvec.getCells();
        
        final int N = reader.numDocs();
        final int M = N/numIntervals;
        
        int subSpaceDimension = Integer.parseInt(prop.getProperty("subspace.dimension"));        
        boolean weightedQ = Boolean.parseBoolean(prop.getProperty("query.weighted", "false"));
        float sigma = Float.parseFloat(prop.getProperty("sigma", "0.1"));
        
        int numSubSpaces = numDimensions/subSpaceDimension;
        assert(numSubSpaces*subSpaceDimension == numDimensions);
        
        ANNList union = null;
        ANNList localIntersection = null;
        int j, k;
        
        //for (j = 0; j < numDimensions; j+=subSpaceDimension) {
        for (j = 0; j < numDimensions; ) {    
            localIntersection = null;
            ANNList intersection = null;
            
             // projected subspace (take intersections across each dimension)            
            //for (k=0; k < subSpaceDimension; k++) {
            for (k=j; k < numDimensions; k++) {        
                //Cell cell = cells[j+k];
                Cell cell = cells[k];
                
                subspaceQuery = weightedQ? cell.constructWeightedQuery(epsilon, sigma) : cell.constructQuery(epsilon);
                
                retrDocs = searcher.search(subspaceQuery, M); 
                
                if (localIntersection == null) {
                    localIntersection = new ANNList(retrDocs);                
                }
                else {
                    ANNList thisDimDist = new ANNList(retrDocs);
                    intersection = ANNList.getIntersection(localIntersection, thisDimDist);
                    
                    if (intersection.neighbors.size() < 100) {                        
                    //if (intersection.neighbors.size() == 0) {                        
                        if (debug) {
                            System.out.println("Took projection: [" + j + ", " + (k-1) + "] " + localIntersection.neighbors.size());
                        }                        
                        break;
                    }
                    else {
                        localIntersection = intersection;
                    }
                }
            }
            
            if (debug) {
                if (k==numDimensions) {
                    System.out.println("Took projection: [" + j + ", " + (k-1) + "] " + localIntersection.neighbors.size());
                }                        
                
            }
            j = k;
                        
            //localIntersection = new ANNList(localIntersection.selectTop(qvec, reader));
            
            // Select the top from this intersection set by similarity scores
            //localIntersection = new ANNList(localIntersection.selectTopKSim(qvec, reader, 100));
            
            //if (localIntersection.neighbors.size() > 0)
            //    localIntersection = new ANNList(localIntersection.selectTop(qvec, reader));

            //if (debug)
            //    System.out.println("Subspace [" + j + ", " + (j+subSpaceDimension-1) +
            //            "]" + ": " + localIntersection.neighbors.size());
            
            if (union == null) {
                union = new ANNList(localIntersection.neighbors);
            }
            else {
                union = ANNList.getUnion(union, localIntersection);
            }
        }
        
        if (debug)
            System.out.println("#Points considered: " + union.neighbors.size());
        
        return union;
    } 
    
    public void searchWithBenchmarkQueries() throws Exception {
        List<DocVector> queries = indexedVecQueries.getQueries();
        int numQueries = queries.size();
        boolean eval = Boolean.parseBoolean(prop.getProperty("eval", "false"));
        int span = Integer.parseInt(prop.getProperty("match.span", "1"));
        
        final float maxDist = (float) Math.sqrt(128);
        
        int rAt1 = 0;
        int retrDoc, relDoc = 0;
        float sumDistShift = 0;
        for (int i=0; i < numQueries; i++) {        
            DocVector qvec = queries.get(i);
            
            System.out.println("Retrieving for query: " + i);
            ANNList anns = retrieve(qvec, span);

            //List<DocVector> retrDocvecs = anns.selectTopK(qvec, reader, 100);
            List<DocVector> retrDocvecs = anns.selectTop(qvec, reader);
            
            retrDoc = retrDocvecs.get(0).getId();
            if (eval)
                relDoc = ((QueryVector)qvec).getNN();
                        
            System.out.println("id(ANN) = " + retrDoc + ", id(NN) = " + relDoc);
            
            float annDist = (float) Math.sqrt(retrDocvecs.get(0).getDistFromQuery());
            float nnDist = (float) Math.sqrt(((QueryVector)qvec).getnnDist());
            System.out.println("dist(ANN) = " + annDist + ", dist(NN) = " + nnDist);
            
            sumDistShift += (annDist-nnDist)/maxDist;
            
            if (eval) {
                int this_r_at_1 = retrDoc==relDoc ||
                        retrDocvecs.get(0).getDistFromQuery()==((QueryVector)qvec).getnnDist()? 1 : 0;            
                rAt1 += this_r_at_1;
                System.out.println("R@1 (" + i + ") = " + this_r_at_1);
                
                //vecQueries.evaluate(reader, i, retrDocvecs);                
            }
        }
        
        if (eval) {
            System.out.println("R@1 = " + rAt1/(float)numQueries);        
            //System.out.println("Jacard = " + vecQueries.avgJacard/(float)numQueries);        
            System.out.println("Dist margin = " + sumDistShift/(float)numQueries);        
        }
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java ANNRetriever <prop-file>");
            args[0] = "init.properties";
        }
        
        try {
            ANNRetriever searcher = new ANNRetriever(args[0]);
            searcher.searchWithBenchmarkQueries();
            searcher.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
    
}
