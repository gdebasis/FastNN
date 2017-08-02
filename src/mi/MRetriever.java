/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mi;

import indexer.DocVector;
import indexer.QueryVector;
import java.io.*;
import java.util.*;
import rndvecgen.RandomQueryGen;
import sift.FloatByteRcd;
import sift.IndexedVecQueries;
import sift.VecQueries;

/**
 *
 * @author Debasis
 */

class SFDScore implements Comparable<SFDScore> {
    int score;
    int docid;

    SFDScore(int docid) {
        this.score = 0;
        this.docid = docid;
    }

    @Override
    public int compareTo(SFDScore that) {
        return Integer.compare(score, that.score);
    }
}


public class MRetriever {
    Properties prop;
    MIndexer mindexer;
    FloatByteRcd q[];
    SFDScore sfdValues[];
    
    boolean syntheticQueries;
    VecQueries indexedVecQueries;
    RandomQueryGen rqgen;
    List<DocVector> queryList;
    
    int numTopRef;
    
    public MRetriever(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));
        
        numTopRef = Integer.parseInt(prop.getProperty("mi.retrieve.refs.ntop"));
        syntheticQueries = prop.getProperty("data.source").equals("synthetic");
        
        // Load the index
        mindexer = MIndexer.load(propFile);
        mindexer.loadRefs();
        
        int nqueries = 0;
        // Load the queries
        if (!syntheticQueries) {
            nqueries = Integer.parseInt(prop.getProperty("mi.numqueries"));
            q = new FloatByteRcd[nqueries];
        }
        else {
            rqgen = new RandomQueryGen(prop);
            q = new FloatByteRcd[rqgen.getNumQueries()];
            
            rqgen.load();
            queryList = rqgen.getQueries();
            int qindex = 0;
            for (DocVector dvec : queryList) {
                q[qindex++] = new FloatByteRcd(qindex, dvec.getVec());
            }
            
            mindexer.loadRandomSamples();
            System.out.println(mindexer.o[0]);
            
            queryList = rqgen.getQueries();
        }
    
        
        if (!syntheticQueries) {
            MIndexer.loadFile(prop.getProperty("queryvecs.file"), q);
            MIndexer.loadFile(prop.getProperty("dvec.file"), mindexer.o);
            indexedVecQueries = new IndexedVecQueries(propFile);        
            queryList = indexedVecQueries.getQueries();
        }
        
        sfdValues = new SFDScore[mindexer.numPoints];
        for (int i=0; i < sfdValues.length; i++) {
            sfdValues[i] = new SFDScore(i);
        }
    }
    
    void resetSFDVals() {
        for (int i=0; i < sfdValues.length; i++) {
            sfdValues[i].score = 0;
        }
    }
    
    List<DistInfo> computeQueryDistFromRefs(int qindex) {
        FloatByteRcd a = this.q[qindex];
        List<DistInfo> distInfos = new ArrayList<>();
        
        for (int j=0; j < mindexer.numRefPoints; j++) {
            FloatByteRcd b = mindexer.r[j];
            float dist = MIndexer.computeDistSquared(a.getVec(), b.getVec());
            DistInfo distInfo = new DistInfo(qindex, j, dist);
            distInfos.add(distInfo);
        }
        
        Collections.sort(distInfos);
        List<DistInfo> topRefs = distInfos.subList(0, numTopRef);
        return topRefs;        
    }
    
    // Compute the approximate NN by making use of the rank information
    // in the metric inverted file.
    // 1. Get the rank information for the query vector from each ref point
    // i.e. compute the relative ordering of the reference points with
    // respect to the query point. Restrict search to the top matches only.
    // 2. Compute the sum over abs rank differences and aggregate the scores    
    // for each data-point. Report the min from this list
    int computeANN(int qindex) {
        
        List<DistInfo> topRefs = computeQueryDistFromRefs(qindex);
        resetSFDVals();
        int refRank;
        
        for (int rank=1; rank <= numTopRef; rank++) {
            DistInfo di = topRefs.get(rank-1);
            int termId = di.refId; // Have to hit the list of this 'term'
            RankInfos postings = mindexer.rinfos[termId];
            
            // Postings are sorted by document (data-point) ids.
            // Traverse the lists and merge by data-point ids
            for (int dataId = 0; dataId < mindexer.numPoints; dataId++) {
                RankInfo rinfo = postings.rankInvList.get(dataId);
                refRank = rinfo == null? mindexer.nTop : rinfo.rank; 

                sfdValues[dataId].docid = dataId;
                sfdValues[dataId].score += Math.abs(rank - refRank);                
            }
        }

        boolean rerank = Boolean.parseBoolean(prop.getProperty("mi.rerank", "false"));
        int numTopToRerank = Integer.parseInt(prop.getProperty("mi.rerank.size", "10"));
        float minDist;
        int ann = 0;

        if (rerank) {
            Arrays.sort(sfdValues);
            minDist = Float.MAX_VALUE;
            for (int i=0; i < numTopToRerank; i++) { // rerank within least SFD scores
                float dist = MIndexer.computeDistSquared(q[qindex].getVec(), this.mindexer.o[sfdValues[i].docid].getVec());
                //System.out.println("id = " + sfdValues[i].docid + " sfd = " + sfdValues[i].score + " dist = " + Math.sqrt(dist));
                if (dist < minDist) {
                    minDist = dist;
                    ann = sfdValues[i].docid;
                }
            }
        }
        else {
            minDist = sfdValues[0].score;
            for (int i = 1; i < mindexer.numPoints; i++) {
                if (sfdValues[i].score < minDist) {
                    ann = i;
                    minDist = sfdValues[i].score;
                }
            }
        }

        return ann;
    }
    
    void computeANNForAll() {
        boolean eval = Boolean.parseBoolean(prop.getProperty("eval", "false"));
        
        int retrDoc = 0, relDoc = 0;        
        float sumDistShift = 0;
        int rAt1 = 0;
        
        float maxDist;
        
        if (!syntheticQueries)
            maxDist = (float) Math.sqrt(mindexer.numDimensions)*
                (mindexer.minMaxInfo.getMax() - mindexer.minMaxInfo.getMin());
        else
            maxDist = (float) Math.sqrt(mindexer.numDimensions)*
                (rqgen.getMax() - rqgen.getMin());
        
        for (int i=0; i < q.length; i++) {
            DocVector qvec = queryList.get(i);
            
            retrDoc = computeANN(i);
            
            if (eval)
                relDoc = ((QueryVector)qvec).getNN();
            System.out.println("id(ANN) = " + retrDoc + ", id(NN) = " + relDoc);
        
            float annDist = (float)Math.sqrt(MIndexer.computeDistSquared(mindexer.o[retrDoc].getVec(), this.q[i].getVec()));
            float nnDist = (float)Math.sqrt(((QueryVector)qvec).getNNDist());
            
            float shift = (annDist-nnDist)/maxDist;
            System.out.println("shift = " + shift);
            sumDistShift += shift; 
            
            if (eval) {
                int this_r_at_1 = retrDoc==relDoc? 1 : 0;            
                rAt1 += this_r_at_1;
                System.out.println("R@1 (" + i + ") = " + this_r_at_1);                
            }            
        }
        if (eval) {
            System.out.println("R@1 = " + rAt1/(float)(q.length));        
            System.out.println("Dist margin = " + sumDistShift/(float)(q.length));        
        }
        
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java MRetriever <prop-file>");
            args[0] = "init_synthetic.properties";
        }

        try {
            MRetriever mir = new MRetriever(args[0]);
            mir.computeANNForAll();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
