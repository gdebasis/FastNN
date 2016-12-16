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
import org.apache.lucene.search.DocIdSetIterator;
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
                DocVector dvec = new DocVector(d, numDimensions, DocVector.numIntervals);
                containedPoints.add(dvec);
            }
        }
        
        return containedPoints;
    } 
    
    boolean validCell() {
        return offsets.size() > 0;
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
