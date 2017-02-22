/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import static indexer.DocVector.FIELD_ID;
import java.io.*;
import java.util.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

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
    
    public static final String SPLIT_CELLS_FIELD = "split_cells";

    public OptimizedRealValuedVecIndexer(String propFile, String inIndexName, String outIndexName) throws Exception {
        super(propFile, outIndexName);  // write to this index
        
        File indexDir = new File(prop.getProperty(inIndexName));
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));        
    }
    
    // Save the split cells which would then be used by the retriever to
    // quantize the query vector.
    void saveSplitCells(IndexWriter writer, List<Cell> splitCells) throws Exception {
        Document doc = new Document();
        StringBuffer buff = new StringBuffer();
        for (Cell cell: splitCells) {
            buff.append(cell).append(" ");
        }
        doc.add(new Field(SPLIT_CELLS_FIELD, buff.toString(), Field.Store.YES, Field.Index.ANALYZED));
        writer.addDocument(doc);
    }
    
    void copyIndex() throws Exception {
        int numDocs = reader.numDocs();
        for (int i=0; i < numDocs; i++) {
            writer.addDocument(reader.document(i));
        }
        
        writer.commit();
    }
    
    void processAllTermWise() throws Exception {
        Cell cell, requantizedCell;
        
        copyIndex();
        IndexReader currentReader = DirectoryReader.open(writer, true);
                        
        List<Cell> splitCells = new ArrayList<>();
        
        Fields fields = MultiFields.getFields(reader);
        Terms terms = fields.terms(DocVector.FIELD_CELL_ID);        
        TermsEnum te = terms.iterator();
        
        // Iterate through every term (a cell id) and requantize the
        // points within the cell if required.
        while (te.next() != null) {
            String cellId = te.term().utf8ToString();
            cell = new Cell(cellId);
            
            if (cell.toSplit(reader)) {
                splitCells.add(cell);
                List<DocVector> containedPoints = cell.getVectors(currentReader, terms, numDimensions);
                
                for (DocVector p : containedPoints) {
                    requantizedCell = cell.quantize(p);  // this function returns a new object
                    p.quantize(requantizedCell);  // update quantization info (cell id)
                    Document doc = p.constructDoc();

                    Term t = new Term(DocVector.FIELD_ID, p.id);
                    writer.deleteDocuments(t);
                    writer.addDocument(doc);
                }
                //Much faster if we don't commit here...
                //writer.commit();
            }
        }

        saveSplitCells(writer, splitCells);

        currentReader.close();
        reader.close();
        writer.close();
    }

    void processAllDocumentWise() throws Exception {
        Cell cell, requantizedCell;
        BytesRef term;
        Terms tfvector;
        TermsEnum termsEnum;
        
        Set<Cell> splitCells = new HashSet<>();

        int numDocs = reader.numDocs();
        
        for (int i=0; i < numDocs; i++) {            
            tfvector = reader.getTermVector(i, DocVector.FIELD_CELL_ID);
            termsEnum = tfvector.iterator(); // access the terms for this field
        
            StringBuffer requantizedCells = new StringBuffer();
            DocVector p = new DocVector(reader.document(i), numDimensions, numIntervals, null);
            
            // iterate for each cell in this document
            while ((term = termsEnum.next()) != null) { // explore the terms for this field
                String cellId = term.utf8ToString(); // one cell id
                cell = new Cell(cellId);
                
                if (cell.toSplit(reader)) { // do we need to requantize?
                    splitCells.add(cell); // mark this cell
                    requantizedCell = cell.quantize(p);  // this function returns a new object
                    System.out.println("Cell " + cell + " updated to " + requantizedCell);
                    
                    requantizedCells.append(requantizedCell).append(" ");
                }
                else {
                    requantizedCells.append(cell).append(" ");
                }
            }
            p.setQuantized(requantizedCells.toString());
            writer.addDocument(p.constructDoc());
        }
        
        saveSplitCells(writer, new ArrayList<Cell>(splitCells));

        reader.close();
        writer.close();
    }
    
    @Override
    public void processAll() throws Exception {
        processAllDocumentWise();
        //processAllTermWise();
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
