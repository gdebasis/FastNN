/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import java.util.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author Debasis
 */
public class Cell {
    int axisId;
    List<Integer> offsets;

    public Cell(int axisId) {
        this.axisId = axisId;
        offsets = new ArrayList<>();
    }
    
    // Copy constructor
    public Cell(Cell that) {
        this.axisId = that.axisId;
        offsets = new ArrayList<>();
        for (int offset : that.offsets) {
            offsets.add(offset);
        }
    }

    // A cell id comprises of <dimension-id>_<offset>+
    public Cell(String cellId) {
        String[] tokens = cellId.split("_");
        offsets = new ArrayList<>();
        
        axisId = Integer.parseInt(tokens[0]);
        for (int i=1; i < tokens.length; i++) {
            offsets.add(Integer.parseInt(tokens[i]));
        }
    }
    
    public Cell getL1Neighbor(int pos) {
        Cell adjCell = new Cell(this);
        int numOffsets = adjCell.offsets.size();
        int lastOffset = adjCell.offsets.get(numOffsets-1);
        lastOffset += pos;
        if (lastOffset < 0)
            return null;
        adjCell.offsets.set(numOffsets-1, lastOffset);
        return adjCell;
    }

    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        
        buff.append(axisId).append("_");
        for (int offset : offsets) {
            buff.append(offset).append("_");
        }
        buff.deleteCharAt(buff.length()-1);
        return buff.toString();
    }
    
    Cell quantize(DocVector vec) {
        
        Cell newCell = new Cell(this);
        
        float delta = (DocVector.MAX_VAL - DocVector.MIN_VAL)/(float)DocVector.numIntervals;
        float cellMin = DocVector.MIN_VAL;
        
        for (int offset : offsets) {
            cellMin += delta * offset;
            delta = delta/(float)DocVector.numIntervals;
        }
        
        int newOffset = (int)((vec.x[axisId] - cellMin)/delta);
        newCell.offsets.add(newOffset);
        return newCell;
    }

    static public Cell constructQuantizedQueryCell(DocVector vec, int axisId, SplitCells splitCells) {
        
        Cell newCell = new Cell(axisId);
        
        float delta = (DocVector.MAX_VAL - DocVector.MIN_VAL)/(float)DocVector.numIntervals;
        float cellMin = DocVector.MIN_VAL;
        int offset;
        Cell splitInfo;
        
        // Additional offsets (in addition to the first one)
        do {
            offset = (int)((vec.x[axisId] - cellMin)/delta);  
            newCell.offsets.add(offset);
            cellMin += delta * offset;
            delta = delta/(float)DocVector.numIntervals;
            splitInfo = splitCells.getSplitInfo(newCell);
        }
        while (splitInfo != null);
            
        return newCell;
    }
    
    Cell getCellIdOfParentCell() {
        Cell parentCell = new Cell(axisId);
        int numOffsets = offsets.size();
        for (int i=0; i < numOffsets-1; i++)
            parentCell.offsets.add(i);
        return parentCell;
    }
    
    // Get the vectors contained within this cell
    List<DocVector> getVectors(IndexReader reader, Terms terms, int numDimensions) throws Exception {
        List<DocVector> containedPoints = new ArrayList<>();
        
        TermsEnum termsEnum = terms.iterator();
        // seek to a specific term
        boolean found = termsEnum.seekExact(new BytesRef(this.toString()));
        
        if (found) {
          // enumerate through documents
            DocsEnum docsEnum = termsEnum.docs(null, null);
            int docid;
            while ((docid = docsEnum.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
                Document d = reader.document(docid);
                DocVector dvec = new DocVector(d, numDimensions, DocVector.numIntervals, null);
                containedPoints.add(dvec);
            }
        }
        
        return containedPoints;
    } 
    
    boolean validCell() {
        return offsets.size() > 0;
    }
    
    public Query constructQuery(int span) {
        BooleanQuery cellLocQuery = new BooleanQuery();
        String cellName = this.toString();
        
        TermQuery tq = new TermQuery(new Term(DocVector.FIELD_CELL_ID, cellName));
        if (span == 0) {
            return tq;
        }
        
        cellLocQuery.add(tq, BooleanClause.Occur.SHOULD);
        
        for (int pos = 1; pos <= span; pos++) {
            Cell prevCell = this.getL1Neighbor(-pos);
            if (prevCell != null)
                cellLocQuery.add(new TermQuery(new Term(DocVector.FIELD_CELL_ID, prevCell.toString())), BooleanClause.Occur.SHOULD);
            Cell nextCell = this.getL1Neighbor(pos);
            if (nextCell != null)            
                cellLocQuery.add(new TermQuery(new Term(DocVector.FIELD_CELL_ID, nextCell.toString())), BooleanClause.Occur.SHOULD);
        }
        
        return cellLocQuery;
    }
    
    public Query constructWeightedQuery(int span, float sigma) {
        BooleanQuery cellLocQuery = new BooleanQuery();
        String cellName = this.toString();
        Cell adjCell;
        
        TermQuery tq = new TermQuery(new Term(DocVector.FIELD_CELL_ID, cellName));
        if (span == 0) {
            return tq;
        }
                
        cellLocQuery.add(tq, BooleanClause.Occur.SHOULD);
        
        for (int pos = 1; pos <= span; pos++) {
            adjCell = this.getL1Neighbor(-pos);
            if (adjCell != null)
                cellLocQuery.add(getWeightedTerm(adjCell, sigma), BooleanClause.Occur.SHOULD);
            adjCell = this.getL1Neighbor(pos);
            if (adjCell != null)            
                cellLocQuery.add(getWeightedTerm(adjCell, sigma), BooleanClause.Occur.SHOULD);
        }
        
        return cellLocQuery;
    }
    
    TermQuery getWeightedTerm(Cell adjCell, float sigma) {
        int u = this.offsets.get(offsets.size()-1);
        int v = adjCell.offsets.get(offsets.size()-1);
        int dist = u-v;
        float alpha = (dist*dist)/(sigma);
        float wt = (float)Math.exp(-alpha);
        TermQuery tq = new TermQuery(new Term(DocVector.FIELD_CELL_ID, adjCell.toString()));
        tq.setBoost(wt);
        return tq;
    }
    
    boolean toSplit(IndexReader reader) throws Exception {
        Cell parentCell = getCellIdOfParentCell();
        int df = 0;
        int numDocs = 0;
        
        Term parentCellTerm = new Term(DocVector.FIELD_CELL_ID, parentCell.toString());
        Term thisCellTerm = new Term(DocVector.FIELD_CELL_ID, this.toString());
        
        // Find the number of cells in this strip, e.g.
        // a. if the current cell is 5_2, 
        numDocs = parentCell.validCell()?  reader.docFreq(parentCellTerm) : reader.numDocs();
        df = reader.docFreq(thisCellTerm);
        
        int uniformCount = numDocs/DocVector.numIntervals;
        return df > uniformCount;
    }
}
