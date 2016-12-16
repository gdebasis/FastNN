/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package retriever;

import indexer.DocVector;
import java.io.File;
import java.io.FileReader;
import java.util.Properties;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author Debasis
 */
public class QuantizedVecSearcher {
    Properties prop;
    IndexReader reader;  // the combined index to search
    IndexSearcher searcher;
    int nwanted;
    int numDimensions;
    int numIntervals;
    boolean includeL1Neighbors;  // max L1 distance from the query cell
    
    public QuantizedVecSearcher(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));                
        nwanted = Integer.parseInt(prop.getProperty("nwanted", "100"));        
        
        numDimensions = Integer.parseInt(prop.getProperty("vec.numdimensions"));
        
        File indexDir = new File(prop.getProperty("index"));
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new LMJelinekMercerSimilarity(0)); // almost close to tf
        
        includeL1Neighbors = Boolean.parseBoolean(prop.getProperty("search.include_L1_neighbors", "false")); // max L1 distance
        
        DocVector.initVectorRange(prop);
        numIntervals = DocVector.numIntervals;
    }
    
    void close() throws Exception {
        reader.close();
    }
    
    // Find the nearest neighbors of this vector
    Query constructQuery(DocVector vector) {
        assert(vector.getNumberofDimensions() == this.numDimensions &&
                vector.getNumberofIntervals() == this.numIntervals);
        
        String qv = vector.getQuantizedString();
        String[] cellNames = qv.split("\\s+");
    
        // Either exact location or one of the neighboring locations
        BooleanQuery cellLocAndNeighborQuery = new BooleanQuery();
        
        BooleanQuery cellLocQuery = new BooleanQuery();
        for (String cellName: cellNames) {
            TermQuery tq = new TermQuery(new Term(DocVector.FIELD_CELL_ID, cellName));
            cellLocQuery.add(tq, BooleanClause.Occur.MUST);
        }
        cellLocAndNeighborQuery.add(cellLocQuery, BooleanClause.Occur.SHOULD);
        
        if (includeL1Neighbors) {
            BooleanQuery cellNeigbborhoodQuery = expandQuery(cellNames);
            cellLocAndNeighborQuery.add(cellNeigbborhoodQuery, BooleanClause.Occur.SHOULD);
        }
        
        return cellLocAndNeighborQuery;
    }
    
    // Expand query to include neighbooring cells (those with
    // a hamming distance of radius from the 
    BooleanQuery expandQuery(String[] cellNames) {
        BooleanQuery queryForAllDimensions = new BooleanQuery();
        
        for (int k=-1; k<=1; k+=2) {
            BooleanQuery queryForThisNeighbor = new BooleanQuery(); 
            
            for (String cellName: cellNames) {
                String[] tokens = cellName.split("_");
                int intervalId = Integer.parseInt(tokens[1]);
            
                String neighboringCellName = tokens[0] + "_" + (intervalId+k);
                TermQuery tq = new TermQuery(new Term(DocVector.FIELD_CELL_ID, neighboringCellName));
                queryForThisNeighbor.add(tq, BooleanClause.Occur.MUST); // OR for a range 
            }
            
            queryForAllDimensions.add(queryForThisNeighbor, BooleanClause.Occur.SHOULD); // AND for all dimensions intersection
        }
        return queryForAllDimensions;
    }
    
    public TopDocs retrieve(DocVector vector) throws Exception {
        TopScoreDocCollector collector = TopScoreDocCollector.create(nwanted);
        Query cellLocQry = constructQuery(vector);
        System.out.println("Lucene qry: " + cellLocQry);
        searcher.search(cellLocQry, collector);
        return collector.topDocs();
    }
    
    void searchWithRandomQueries() throws Exception {
        int numSamples = Integer.parseInt(prop.getProperty("syntheticdata.numqueries", "1"));
        for (int i=0; i < numSamples; i++)
            searchWithRandomQuery();
    }
    
    void searchWithRandomQuery() throws Exception {
        DocVector randomQry = new DocVector(numDimensions, numIntervals);
        System.out.println("Query: " + randomQry);
        TopDocs topDocs = retrieve(randomQry);
        System.out.println("Total matches: " + topDocs.totalHits);        
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java TweetIndexer <prop-file>");
            args[0] = "init.properties";
        }
        
        try {
            QuantizedVecSearcher qvsearcher = new QuantizedVecSearcher(args[0]);
            qvsearcher.searchWithRandomQueries();
            qvsearcher.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
}

/*
        ByteBuffer buff2 = ByteBuffer.wrap(bytes);
        float[] x2 = new float[x.length];
        for (int i=0; i < x2.length; i++) {
            x2[i] = buff2.getFloat();
            assert(x2[i] == x[i]);
        }
        

*/