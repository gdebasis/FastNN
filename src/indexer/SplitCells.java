/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import java.util.HashMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

/**
 * Store the split cells information.
 * 
 * In order to be able to quantize a vector along a particular axis
 * at query time, we need to store what cells have been split.
 * 1_21_34 suggests that the cell 1_21 has been
 * split further. In this case, store 1_21 in the table to indicate that
 * this cell has been split further. By default a cell is split once.
 * 
 * @author Debasis
 */
public class SplitCells {
    HashMap<String, Cell> splitInfo;
    
    public SplitCells() {
        splitInfo = new HashMap<>();
    }
    
    void addSplit(Cell cell) {
        //Cell parentCell = cell.getCellIdOfParentCell();
        splitInfo.put(cell.toString(), cell);
    }
    
    Cell getSplitInfo(Cell cell) {
        return splitInfo.get(cell.toString());
    }
    
    public static SplitCells readFromIndex(IndexReader reader) throws Exception {
        SplitCells splitCells = new SplitCells();

        // The last document contains the split information.
        int numDocs = reader.maxDoc();
        Document splitCellInfoDoc = reader.document(numDocs-1);
        
        String splitCellsInfo = splitCellInfoDoc.get(OptimizedRealValuedVecIndexer.SPLIT_CELLS_FIELD);
        if (splitCellsInfo == null)
            return null;
        
        String[] tokens = splitCellsInfo.split("\\s+");
        for (String token : tokens) {
            Cell cell = new Cell(token);
            splitCells.addSplit(cell);
        }
        
        return splitCells;
    }
}
