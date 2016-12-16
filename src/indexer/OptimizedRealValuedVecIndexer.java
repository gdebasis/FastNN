/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import java.io.*;
import java.util.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import retriever.QuantizedVecSearcher;

/**
 *
 * @author Debasis
 * 
 * Optimize by analyzing frequency distributions along each axis.
 * 
 * Algorithm outline:
 * For each dimension d
 *      Split into 10 equal sized intervals and compute frequency distribution in each.
 *      If max_freq(cell)/sum_freq(cell) > threshold, then split into 100 and repeat.
 * Keep track of the interval size for this dimension in the pseudo-term corresponding
 * to this dimension, i.e. instead of keeping <dimension> <offset>, we store
 * <dimension> <number of intervals> <offset>
 * 
 */
public class OptimizedRealValuedVecIndexer extends RealValuedVecIndexer {
    IndexReader reader;

    public OptimizedRealValuedVecIndexer(String propFile, String inIndexName, String outIndexName) throws Exception {
        super(propFile, outIndexName);  // write to this index
        
        File indexDir = new File(prop.getProperty(inIndexName));
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));        
    }
    
    @Override
    void processAll() throws Exception {
        Cell cell, requantizedCell;
        
        Fields fields = MultiFields.getFields(reader);
        Terms terms = fields.terms(DocVector.FIELD_CELL_ID);        
        TermsEnum te = terms.iterator();
        
        // Iterate through every term (a cell id) and requantize the
        // points within the cell if required.
        while (te.next() != null) {
            String cellId = te.term().utf8ToString();
            cell = new Cell(cellId);
            
            if (cell.toSplit(reader)) {
                List<DocVector> containedPoints = cell.getVectors(reader, terms, numDimensions);
                
                for (DocVector p : containedPoints) {
                    requantizedCell = cell.quantize(p);  // this function returns a new object
                    p.quantize(requantizedCell);  // update quantization info (cell id)
                    Document doc = p.constructDoc();
                    writer.addDocument(doc);
                }
            }
        }

        reader.close();
        writer.close();
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java OptimizedRealValuedVecIndexer <prop-file>");
            args[0] = "init.properties";
        }
        
        try {
            OptimizedRealValuedVecIndexer rvIndexer = new OptimizedRealValuedVecIndexer(args[0], "index", "optimized.index");
            rvIndexer.processAll();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }        
}
