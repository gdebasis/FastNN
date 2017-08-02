/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rndvecgen;

import indexer.DocVector;
import indexer.QueryVector;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Properties;
import sift.VecQueries;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author Debasis
 */
public class RandomQueryGen extends RandomVecGen {
    VecQueries queries;
    int numQueries;
    String vecFileName;
    List<String> vecLines;
    int numDocs;
            
    public RandomQueryGen(Properties prop) throws Exception {
        super(prop);
        queries = new VecQueries();
        numQueries = Integer.parseInt(prop.getProperty("syntheticdata.numqueries"));
        vecFileName = this.randomSamplesFileName();        
        vecLines = FileUtils.readLines(new File(vecFileName));
        numDocs = vecLines.size();
    }
    
    public RandomQueryGen(String propFile) throws Exception {
        super(propFile);        
        queries = new VecQueries();
        numQueries = Integer.parseInt(prop.getProperty("syntheticdata.numqueries"));
        vecFileName = this.randomSamplesFileName();        
        vecLines = FileUtils.readLines(new File(vecFileName));
        numDocs = vecLines.size();
    }
    
    void sampleQueries() {
        boolean usingMixtureDist = prop.getProperty("syntheticdata.query.genmode", "prior").equals("prior");
        QueryVector qvec;
        for (int i=0; i < numQueries; i++) {
            qvec = usingMixtureDist? new QueryVector(mixtureDist, i) : new QueryVector(i, numDimensions);
            queries.add(qvec);
        }
    }
    
    public int getNumQueries() { return numQueries; }
    
    void setNNInfos() throws Exception {
        for (DocVector qv : queries.getQueries()) {
            setNN((QueryVector)qv);
        }
    }
    
    void setNN(QueryVector qvec) throws Exception {
        int numDimensions = qvec.getNumberofDimensions();
        int numIntervals = qvec.getNumberofIntervals();

        float minDist = Float.MAX_VALUE;
        int minDocId = 0;
        DocVector minDocVector = null;
        float dist;

        System.out.println("Computing NN info between vecs: ");
        
        for (int i = 0; i < numDocs; i++) {
            DocVector dvec = new DocVector(vecLines.get(i), numDimensions, numIntervals);

            dist = dvec.getDist(qvec);
            if (dist < minDist) {
                minDist = dist;
                minDocId = i;
                minDocVector = dvec;
            }
        }
        
        qvec.setNNInfo(minDocId, minDist);

        System.out.println("Query: " + qvec);
        System.out.println("NN-vec wrt query: " + minDocVector);
    }
    
    public void generateAndSaveQueries() throws Exception {
        sampleQueries();
        setNNInfos();
        save();
    }

    public void save() throws Exception {
        
        String qvecFileName = this.randomSamplesFileName() + "queries.txt";
        String distInfoFile = this.randomSamplesFileName() + "queries.nninfo.txt";

        FileWriter fw = new FileWriter(qvecFileName);
        BufferedWriter bw = new BufferedWriter(fw);
        
        for (DocVector vec : this.queries.getQueries()) {
            bw.write(this.vecToStr(vec.getId(), vec.getVec()));            
        }
        bw.close();
        fw.close();
        
        fw = new FileWriter(distInfoFile);
        bw = new BufferedWriter(fw);
        
        for (DocVector vec : this.queries.getQueries()) {
            QueryVector qvec = (QueryVector)vec;
            bw.write(qvec.getNN() + "\t" + qvec.getNNDist() + "\n");            
        }
        
        bw.close();
        fw.close();
    }
    
    public void load() throws Exception {
        String qvecFileName = this.randomSamplesFileName() + "queries.txt";
        String distInfoFile = this.randomSamplesFileName() + "queries.nninfo.txt";

        List<String> nnInfoLines = FileUtils.readLines(new File(distInfoFile));
        List<String> qvecLines = FileUtils.readLines(new File(qvecFileName));
        
        assert(nnInfoLines.size() == qvecLines.size());
        
        int i = 0;
        for (String qvecLine : qvecLines) {
            String nnInfo = nnInfoLines.get(i);
            String[] tokens = nnInfo.split("\\s+");
            int nnId = Integer.parseInt(tokens[0]);
            float dist = Float.parseFloat(tokens[1]);
            this.queries.add(new QueryVector(qvecLine, numDimensions, DocVector.numIntervals, nnId, dist));
            i++;
        }
    }
    
    public List<DocVector> getQueries() throws Exception {
        return queries.getQueries();
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java RealValuedVecIndexer <prop-file>");
            args[0] = "init_synthetic.properties";
        }
        
        try {
            RandomQueryGen rqg = new RandomQueryGen(args[0]);
            rqg.generateAndSaveQueries();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
}
