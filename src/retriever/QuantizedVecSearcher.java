/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package retriever;

import indexer.Cell;
import indexer.DocVector;
import indexer.SplitCells;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.lucene.document.Document;
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
    SplitCells splitCells;
    
    public QuantizedVecSearcher(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));                
        nwanted = Integer.parseInt(prop.getProperty("nwanted", "100"));        
        
        numDimensions = Integer.parseInt(prop.getProperty("vec.numdimensions"));
        
        // Read from optimized index (instead of the initial index)
        String indexPath = prop.getProperty("optimized.index");
        if (indexPath != null) {            
            File indexDir = new File(indexPath);

            reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
            searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new LMJelinekMercerSimilarity(0.1f)); // almost close to tf        
            splitCells = SplitCells.readFromIndex(reader);
        }
        
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
        
        //System.out.println("Lucene qry: " + cellLocQry);
        
        searcher.search(cellLocQry, collector);
        return collector.topDocs();
    }

    BooleanQuery addL1Neighbors(BooleanQuery query, Cell prevCell, Cell nextCell) {
        // copy existing query into new query
        BooleanQuery newQuery = new BooleanQuery();
        BooleanClause[] clauses = query.getClauses();
        
        for (int i=0; i < clauses.length-1; i++) { // copy all but the last clause
            newQuery.add(clauses[i]);
        }
        
        BooleanQuery expansionQry = new BooleanQuery();
        // additional terms
        expansionQry.add(new TermQuery(new Term(DocVector.FIELD_CELL_ID, prevCell.toString())), BooleanClause.Occur.SHOULD);
        expansionQry.add(new TermQuery(new Term(DocVector.FIELD_CELL_ID, nextCell.toString())), BooleanClause.Occur.SHOULD);
        
        newQuery.add(expansionQry, BooleanClause.Occur.MUST);
        
        return newQuery;
    }
    
    /*
        A strict Boolean query satisfying a match in term (cells) in all
        dimensions is highly unlikely. The key idea is therefore to try
        building a query incrementally.
    */
    public TopDocs retrieveWithIncrementalQuery(DocVector qvec) throws Exception {
        TopDocs topDocs = null;
        Cell[] cells = qvec.getCells();
        
        BooleanQuery cellLocQuery = new BooleanQuery();
        BooleanQuery incrQuery = null;
        
        int span = Integer.parseInt(prop.getProperty("match.span", "3"));
        for (Cell cell: cells) {
            
            //String cellName = cell.toString();            
            //TermQuery tq = new TermQuery(new Term(DocVector.FIELD_CELL_ID, cellName));
            //cellLocQuery.add(tq, BooleanClause.Occur.MUST);
            
            // Define an interval of match (x-delta, x+delta)
            cellLocQuery.add(cell.constructQuery(span), BooleanClause.Occur.MUST);
                        
            int pos = 0;
            topDocs = searcher.search(cellLocQuery, nwanted);
            
            incrQuery = null;
            while (topDocs.scoreDocs.length == 0) {
                pos++;
                Cell prevCell = cell.getL1Neighbor(-pos);
                Cell nextCell = cell.getL1Neighbor(pos);
                
                // Adding this query term doesn't produce an exact match.
                // Allow for leniency in matching. Try the adjacent cells, i.e.
                // pos-1, pos+1 and continue in this way (ensures that distance
                // from pos is also taken into account).                
                incrQuery = addL1Neighbors(cellLocQuery, prevCell, nextCell);
                topDocs = searcher.search(incrQuery, nwanted);
            }
            
            if (incrQuery != null)  // if query was changed...
                cellLocQuery = incrQuery;            
        }
        return topDocs;
    }

    List<DocVector> mergeTopDocs(List<DocVector> a, List<DocVector> b) {
        HashMap<Integer, DocVector> docIdMap = new HashMap<>();
        
        for (DocVector dvec : a) {
            if (!docIdMap.containsKey(dvec.getId()))
                docIdMap.put(dvec.getId(), dvec);
        }
        for (DocVector dvec : b) {
            if (!docIdMap.containsKey(dvec.getId()))
                docIdMap.put(dvec.getId(), dvec);
        }
                
        List<DocVector> mergedDocsList = new ArrayList<>();
        for (Map.Entry<Integer, DocVector> e : docIdMap.entrySet())
            mergedDocsList.add(e.getValue());
        
        Collections.sort(mergedDocsList);        
        return mergedDocsList.subList(0, Math.min(nwanted, mergedDocsList.size()));  // keep the top numwanted
    }
    
    List<Integer> mergeDocIdList(List<Integer> a, List<Integer> b) {
        HashMap<Integer, Integer> docIdMap = new HashMap<>();
        
        for (Integer a_i : a) {
            if (!docIdMap.containsKey(a_i))
                docIdMap.put(a_i, a_i);
        }
        for (Integer b_i : a) {
            if (!docIdMap.containsKey(b_i))
                docIdMap.put(b_i, b_i);
        }
                
        List<Integer> mergedDocsList = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : docIdMap.entrySet())
            mergedDocsList.add(e.getValue());
        
        return mergedDocsList;  // keep the top numwanted
    }
    
    public List<DocVector> retrieveWithPivotedRelaxedQuery(
                            IndexSearcher searcher,
                            DocVector qvec,
                            int pivotDimension,
                            HashSet<Integer> dimensions) throws Exception {
        
        if (!dimensions.contains(pivotDimension))
            return null;
        
        TopDocs topDocs = null;
        Cell[] cells = qvec.getCells();
        
        BooleanQuery cellLocQuery = new BooleanQuery();
        BooleanQuery incrQuery = null;
        
        int span = Integer.parseInt(prop.getProperty("match.span", "3"));
        
        for (int j = pivotDimension; j < qvec.numDimensions; j++) {
            if (!dimensions.contains(j))
                continue;
            
            Cell cell = cells[j];
            
            // Define an interval of match (x-delta, x+delta)
            cellLocQuery.add(cell.constructQuery(span), BooleanClause.Occur.MUST);
                        
            topDocs = searcher.search(cellLocQuery, nwanted);
            //if (topDocs.scoreDocs.length < nwanted) { // remove this term!
            if (topDocs.scoreDocs.length < 10) { // remove this term!
            //if (topDocs.scoreDocs.length == 0) { // remove this term!
                incrQuery = new BooleanQuery();
                BooleanClause[] clauses = cellLocQuery.getClauses();
                for (int i=0; i < clauses.length-1; i++)
                    incrQuery.add(clauses[i]);
                
                cellLocQuery = incrQuery;
                topDocs = searcher.search(cellLocQuery, nwanted);
            }
            else {
                // this was a successful addition...
                // don't consider this dimension further...
                dimensions.remove(j);
            }
        }
        
        List<DocVector> retrievedDocVecs = rerankByEuclideanDist(qvec, searcher, topDocs);        
        //List<Integer> retrievedDocVecs = getRetrievedDocIds(topDocs);
        return retrievedDocVecs;
    } 

    public TopDocs retrieveWithPivotedRelaxedWeightedQuery(DocVector qvec,
            int pivotDimension,
            HashSet<Integer> dimensions) throws Exception {
        
        if (!dimensions.contains(pivotDimension))
            return null;
        
        TopDocs topDocs = null;
        Cell[] cells = qvec.getCells();
        
        BooleanQuery cellLocQuery = new BooleanQuery();
        BooleanQuery incrQuery = null;
        
        int nwanted = 5;
        
        int span = Integer.parseInt(prop.getProperty("match.span", "3"));
        float sigma = Float.parseFloat(prop.getProperty("sigma", "1"));
        
        for (int j = pivotDimension; j < qvec.numDimensions; j++) {
            if (!dimensions.contains(j))
                continue;
            
            Cell cell = cells[j];
            
            // Define an interval of match (x-delta, x+delta)
            cellLocQuery.add(cell.constructWeightedQuery(span, sigma), BooleanClause.Occur.MUST);
                        
            topDocs = searcher.search(cellLocQuery, nwanted);
            if (topDocs.scoreDocs.length < nwanted) { // remove this term!
            //if (topDocs.scoreDocs.length == 0) { // remove this term!
                incrQuery = new BooleanQuery();
                BooleanClause[] clauses = cellLocQuery.getClauses();
                for (int i=0; i < clauses.length-1; i++)
                    incrQuery.add(clauses[i]);
                
                cellLocQuery = incrQuery;
                topDocs = searcher.search(cellLocQuery, nwanted);
            }
            else {
                // this was a successful addition...
                // don't consider this dimension further...
                dimensions.remove(j);
            }
        }
        
        System.out.println(cellLocQuery);
        return topDocs;
    } 
    
    public List<DocVector> retrieveWithPivotedRelaxedQueries(IndexSearcher searcher, DocVector qvec) throws Exception {
        HashSet<Integer> dimensionSet = new HashSet<>();
        for (int i=0; i < qvec.numDimensions; i++)
            dimensionSet.add(i);
        
        List<DocVector> aggregatedList = retrieveWithPivotedRelaxedQuery(searcher, qvec, 0, dimensionSet);

        // Iterate while there're more dimensions to explore...
        for (int i=1; i < qvec.numDimensions && dimensionSet.size() > 0; i++) {
            // Explore a subspace and don't consider these dimenions further...
            List<DocVector> thisList = retrieveWithPivotedRelaxedQuery(searcher, qvec, i, dimensionSet);
            
            if (thisList != null)
                aggregatedList = mergeTopDocs(aggregatedList, thisList);
        }
        
        return aggregatedList;
        
        /*
        List<Integer> aggregatedList = retrieveWithPivotedRelaxedQuery(qvec, 0);
        
        for (int i=1; i < qvec.numDimensions; i++) {
            List<Integer> thisList = retrieveWithPivotedRelaxedQuery(qvec, i);
            //aggregatedList = mergeTopDocs(aggregatedList, thisList);
            aggregatedList = mergeDocIdList(aggregatedList, thisList);
        }
        
        List<DocVector> nnList = new ArrayList<>();
        for (int id : aggregatedList) {
            Document d = reader.document(id);            
            DocVector dvec = new DocVector(d, numDimensions, numIntervals);
            dvec.setDistWithQry(qvec.getDist(dvec));
            nnList.add(dvec);
        }
        
        Collections.sort(nnList);
        return nnList.subList(0, nwanted);
        */
    }

    // Uses the similarity values instead of reranking by the actual Euclidean
    // distances while merging the top docs across pivor iterations.
    // Also uses weighted queries... the weights are set for the L1-neighbour terms
    // inversely proportional to the L1 distances. 
    public List<DocVector> retrieveWithPivotedRelaxedWeightedQueries(DocVector qvec) throws Exception {
        List<TopDocs>topDocs = new ArrayList<>();
        
        HashSet<Integer> dimensionSet = new HashSet<>();
        for (int i=0; i < qvec.numDimensions; i++)
            dimensionSet.add(i);
        
        // Iterate while there're more dimensions to explore...
        for (int i=0; i < qvec.numDimensions && dimensionSet.size() > 0; i++) {
            
            // Explore a subspace and don't consider these dimenions further...
            TopDocs thisTopDocs = retrieveWithPivotedRelaxedWeightedQuery(qvec, i, dimensionSet);
            if (thisTopDocs != null)
                topDocs.add(thisTopDocs);
                
        }
        
        TopDocs[] topDocsArray = new TopDocs[topDocs.size()];
        TopDocs mergedTopDocs = TopDocs.merge(nwanted, topDocs.toArray(topDocsArray));
        
        return rerankByEuclideanDist(qvec, searcher, mergedTopDocs);
    }
    
    public TopDocs retrieveWithRelaxedQuery(DocVector qvec) throws Exception {
        TopDocs topDocs = null;
        Cell[] cells = qvec.getCells();
        
        BooleanQuery cellLocQuery = new BooleanQuery();
        BooleanQuery incrQuery = null;
        
        int span = Integer.parseInt(prop.getProperty("match.span", "3"));
        
        for (Cell cell: cells) {
            
            // Define an interval of match (x-delta, x+delta)
            cellLocQuery.add(cell.constructQuery(span), BooleanClause.Occur.MUST);
                        
            topDocs = searcher.search(cellLocQuery, nwanted);
            if (topDocs.scoreDocs.length < nwanted) { // remove this term!
                incrQuery = new BooleanQuery();
                BooleanClause[] clauses = cellLocQuery.getClauses();
                for (int i=0; i < clauses.length-1; i++)
                    incrQuery.add(clauses[i]);
                
                cellLocQuery = incrQuery;
                topDocs = searcher.search(cellLocQuery, nwanted);
            }
        }
        System.out.println("#query terms = " + cellLocQuery.getClauses().length);
        
        return topDocs;
    }
    
    
    void searchWithRandomQueries() throws Exception {
        int numSamples = Integer.parseInt(prop.getProperty("syntheticdata.numqueries", "1"));
        for (int i=0; i < numSamples; i++)
            searchWithRandomQuery(i);
    }
    
    List<DocVector> rerankByEuclideanDist(DocVector queryVec, IndexSearcher searcher, TopDocs topDocs) throws Exception {
        IndexReader reader = searcher.getIndexReader();
        List<DocVector> nnList = new ArrayList<>();
        int rank = 1;
        for (ScoreDoc sd : topDocs.scoreDocs) {
            Document d = reader.document(sd.doc);
            
            DocVector dvec = new DocVector(d, numDimensions, numIntervals);
            float dist = queryVec.getDist(dvec);
            dvec.setDistWithQry(dist);
            //System.out.println("Doc " + sd.doc + " with distance " + dist + " retrieved at rank: " + rank + " (Sim = " + sd.score + ")");
            nnList.add(dvec);
            rank++;
        }
        Collections.sort(nnList);
        return nnList;
    }
    
    List<Integer> getRetrievedDocIds(TopDocs topDocs) throws Exception {
        List<Integer> nnList = new ArrayList<>();
        for (ScoreDoc sd : topDocs.scoreDocs) {
            nnList.add(sd.doc);
        }
        return nnList;
    }
    
    void searchWithRandomQuery(int id) throws Exception {
        
        int numDocs = reader.numDocs();
        int randIndex = (int)(Math.random()*numDocs);        
        Document doc = reader.document(randIndex);
        
        DocVector randomQry = new DocVector(doc, numDimensions, numIntervals, splitCells);
        System.out.println("Random Query: (docid = " + randIndex + ") " + randomQry);
        randomQry.addNoise(0.000001f); // add small noise to components
        System.out.println("Random Query after adding noise: " + randomQry);
        
        System.out.println("Query: " + randomQry);
        TopDocs topDocs = retrieve(randomQry);
        System.out.println("Total matches: " + topDocs.totalHits);        
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java QuantizedVecSearcher <prop-file>");
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
