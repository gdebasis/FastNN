/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sift;

import indexer.DocVector;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.TopDocs;

/**
 *
 * @author Debasis
 */

public class VecQueries {
    Properties prop;
    List<DocVector> qvecs;
    Qrels nnData;
    
    public float[] rAt; // aggregated recall at 1, 10, 100
    
    public VecQueries(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));        
        
        qvecs = new ArrayList<>();
        DocVector.initVectorRange(prop);
        rAt = new float[3];
        
        load();
    }
    
    public List<DocVector> getQueries() { return qvecs; }
    
    public Qrels getQrels() { return nnData; }
    
    final void loadQueries() throws Exception {
        String fileName = prop.getProperty("queryvecs.file");
        boolean normalize = Boolean.parseBoolean(prop.getProperty("normalize", "false"));
        
        RandomAccessFile reader = new RandomAccessFile(fileName, "r");
        
        int count = 0;
        FloatByteRcd fbr = null;
        do {
            fbr = FloatByteRcd.readNext(reader, count);
            if (fbr == null)
                break;
            
            DocVector dvec = fbr.getDocVec(normalize);
            qvecs.add(dvec);
            count++;
        }
        while (true);
    }

    final void loadQrels() throws Exception {
        nnData = new Qrels(qvecs.size());
        nnData.load(prop.getProperty("qrels.file"));        
    }
    
    final void load() throws Exception {        
        loadQueries();
        loadQrels();
    }
    
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff.append("Queries:").append("\n");
        for (DocVector qvec : this.qvecs) {
            buff.append(qvec).append("\n");
        }
        buff.append("Qrels: (").append(nnData.relVecIds.get(0).length).append(")\n");
        buff.append(nnData).append("\n");
        
        return buff.toString();
    }
    
    public void evaluate(IndexReader reader, int id, List<DocVector> topDocs) throws Exception {
        int nnVecId;
        int i;

        DocVector qvec = this.qvecs.get(id);
        List<DocVector> relVecs = nnData.getSortedRelVecs(reader, qvec, id);
        
        /*
        i = 1;
        for (DocVector relVec : relVecs) {
            System.out.println("Distance between query and " + i + "-NN (" + relVec.getId() + ") = " + relVec.getDistFromQuery());
            i++;
        }
        
        i = 0;
        for (DocVector dvec: topDocs) {
            System.out.println("Distance between query and doc(" + dvec.getId() + ") = " + dvec.getDistFromQuery());
            i++;
        }
        */
        
        nnVecId = relVecs.get(0).getId();
        int nnRetrievedAt = -1;
        
        i = 0;
        StringBuffer buff = new StringBuffer();
        for (DocVector d : topDocs) {
            if (d.getId() == nnVecId) {
                nnRetrievedAt = i;
                break;
            }
            buff.append(d.getId()).append(",");
            i++;
        }
        
        //System.out.println("1-NN: " + nnVecId);
        //System.out.println("Retrieved docs: " + buff.toString());
        
        if (nnRetrievedAt < 0)
            return;
        
        if (nnRetrievedAt < 100) {
            rAt[2]++;
            rAt[1]++;
            rAt[0]++;
        }        
        else if (nnRetrievedAt < 10) {
            rAt[1]++;
            rAt[0]++;            
        }
        else if (nnRetrievedAt < 1) {
            rAt[0]++;                        
        }
    }            
}

