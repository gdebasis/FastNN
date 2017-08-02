/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mi;

import java.util.*;
import java.io.*;
import org.apache.commons.io.FileUtils;
import rndvecgen.RandomVecGen;
import sift.FloatByteRcd;

/**
 * The metric inverted file baseline as proposed in Amato et.al. (2014)
 * Store the MI file as a Java serialized object.
 * @author Debasis
 */

class RankInfo implements Comparable<RankInfo> {
    int id; // id of the point
    int rank; // distance rank => lower the distance better (closer to 1) the rank

    public RankInfo(int id, int rank) {
        this.id = id;
        this.rank = rank;
    }
    
    public RankInfo(String token) {
        String[] subtokens = token.split(":");
        id = Integer.parseInt(subtokens[0]);
        rank = Integer.parseInt(subtokens[1]);
    }

    @Override
    public int compareTo(RankInfo t) {
        return Integer.compare(id, t.id);
    }
    
    @Override
    public String toString() { return id + ":" + rank; }
}

class RankInfos {
    HashMap<Integer, RankInfo> rankInvList;
    
    public RankInfos() {
        rankInvList = new HashMap<>();
    }
    
    public RankInfos(String line) { // when read from file
        rankInvList = new HashMap<>();
        String[] tokens = line.split("\\s+");
        for (String token : tokens) {
            add(new RankInfo(token));
        }
    }
    
    public void add(RankInfo rinfo) {
        rankInvList.put(rinfo.id, rinfo);
    }
    
    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (RankInfo rinfo : rankInvList.values()) {
            buff.append(rinfo).append(" ");
        }
        return buff.toString();
    }
}

class DistInfo implements Comparable<DistInfo> {
    int id;
    int refId;
    float dist;

    public DistInfo(int id, int refId, float dist) {
        this.id = id;
        this.refId = refId;
        this.dist = dist;
    }
    
    @Override
    public int compareTo(DistInfo t) {
        return Float.compare(dist, t.dist);
    }
}

public class MIndexer {
    Properties prop;
    int numRefPoints;
    int numPoints;
    int numDimensions;
    FloatByteRcd[] o; // non-ref vecs
    FloatByteRcd[] r; // ref-vecs
    RankInfos[] rinfos;
    int startIdOfRef;
    int nTop;
    FloatByteRcd minMaxInfo;
    boolean syntheticQueries;
    RandomVecGen rvgen;
    
    public MIndexer(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));        
        
        syntheticQueries = prop.getProperty("data.source").equals("synthetic");
        numDimensions = Integer.parseInt(prop.getProperty("vec.numdimensions"));

        rvgen = new RandomVecGen(prop);
        if (!syntheticQueries) {
            minMaxInfo = new FloatByteRcd(prop.getProperty("dvec.file"));                    
            numPoints = Integer.parseInt(prop.getProperty("mi.npoints"));
        }
        else {
            numPoints = rvgen.getNumPoints();
        }
        
        numRefPoints = Integer.parseInt(prop.getProperty("mi.nref"));
        nTop = Integer.parseInt(prop.getProperty("mi.ntop"));
        
        o = new FloatByteRcd[numPoints];
        r = new FloatByteRcd[numRefPoints];
        rinfos = new RankInfos[numRefPoints];
        for (int i=0; i<numRefPoints; i++) {
            rinfos[i] = new RankInfos();
        }
    }
    
    static public int loadFile(String fileName, FloatByteRcd[] o) throws Exception {
        File file = new File(fileName);
        
        RandomAccessFile reader = new RandomAccessFile(file, "r");
        
        final int batchSize = 1000;
        int count = 0;

        FloatByteRcd fbr = null;
        do {
            fbr = FloatByteRcd.readNext(reader, count);
            if (fbr == null)
                break;
            
            if (count%batchSize == 0) {
                System.out.println("Loaded " + count + " vectors...");
            }

            o[count++] = fbr;
        }        
        while (true);        
        return count;
    }
   
    void chooseRandomRef() throws Exception {
        float min = 0, max = 0;
        if (!syntheticQueries) {
            System.out.println("min=" + minMaxInfo.getMin() + ", max=" + minMaxInfo.getMax());
            min = minMaxInfo.getMin();
            max = minMaxInfo.getMax();
        }
        else {
            min = rvgen.getMin();
            max = rvgen.getMax();
        }
        
        boolean usePrior = prop.getProperty("mi.ref.mode").equals("prior");
        
        for (int i=0; i < numRefPoints; i++) {
            if (!usePrior)
                this.r[i] = FloatByteRcd.generateRandom(startIdOfRef+i, numDimensions, min, max);
            else
                this.r[i] = FloatByteRcd.generateRandom(i, rvgen);
        }
    }
    
    void saveRefs() throws Exception {
        String outRefFileName = prop.getProperty("mi.ref.out");
        FileWriter fw = new FileWriter(outRefFileName);
        BufferedWriter bw = new BufferedWriter(fw);
        
        for (int i=0; i<numRefPoints; i++) {
            bw.write(this.r[i].toString() + "\n");
        }
        
        bw.close();
        fw.close();
    }
    
    void loadRefs() throws Exception {
        String refFileName = prop.getProperty("mi.ref.out");
        FileReader fr = new FileReader(refFileName);
        BufferedReader br = new BufferedReader(fr);
        
        String line;
        int i = 0;
        while ((line = br.readLine()) != null) {
            this.r[i] = new FloatByteRcd(i, line);
            i++;
        }
        br.close();
        fr.close();
    }
    
    static public float computeDistSquared(float[] x, float[] y) {
        float sum = 0;
        for (int i=0; i < x.length; i++) {
            sum += (x[i] - y[i])*(x[i] - y[i]);
        }
        return sum;
    }
    
    void computeDistRanksFromRef() {
        int i, j, k;
    
        TreeSet<DistInfo> nnList;
        
        DistInfo distInfo;
        
        for (j=0; j < numRefPoints; j++) {
            FloatByteRcd b = this.r[j];
            nnList = new TreeSet<>();
            
            System.out.println("Computing distance info for ref-point: " + j);
            
            for (i=0; i < numPoints; i++) {                
            
                FloatByteRcd a = this.o[i];
                
                float dist = computeDistSquared(a.getVec(), b.getVec());
                distInfo = new DistInfo(i, j, dist);
            
                nnList.add(distInfo);
            }
            
            if (i%1000==0)
                System.out.println("Trimming distance info for point: " + j);
            
            Iterator<DistInfo> iter = nnList.iterator();
            // Get the best-k
            for (k=0; k < nTop; k++) {
                DistInfo nnInfo = iter.next();
                rinfos[nnInfo.refId].add(new RankInfo(nnInfo.id, k+1));
            }
        }        
    }
    
    void save() throws Exception {
        String outFile = prop.getProperty("mifile.name");
        FileWriter fw = new FileWriter(outFile);
        BufferedWriter bw = new BufferedWriter(fw);
        
        for (int i=0; i < numRefPoints; i++) {
            bw.write(rinfos[i].toString() + "\n");
        }
        bw.close();
        fw.close();
        
        saveRefs();
    }
    
    static public MIndexer load(String propFile) throws Exception {
        
        String line;
        MIndexer mifile = new MIndexer(propFile);
        
        FileReader fr = new FileReader(mifile.prop.getProperty("mifile.name"));
        BufferedReader br = new BufferedReader(fr);
        
        int refId = 0;
        while ((line = br.readLine()) != null) {
            if (line.trim().length() == 0) {
                refId++;
                continue;
            }
            mifile.rinfos[refId] = new RankInfos(line);
            refId++;
        }
        
        return mifile;
    }
    
    int indexRandom() throws Exception {
        
        final int batchSize = 10000;
        int count = 0, totalCount = 0;
        
        for (int i = 0; i < numPoints; i++) {            
            o[totalCount] = FloatByteRcd.generateRandom(i, rvgen);
            if (count == batchSize) {
                System.out.println("Added " + totalCount + " vectors...");
                count = 0;           
            }
            count++;
            totalCount++;
        }
        saveRandomSamples();
        return totalCount;
    }
    
    void saveRandomSamples() throws Exception {
        FileWriter fw = new FileWriter(rvgen.randomSamplesFileName());
        BufferedWriter bw = new BufferedWriter(fw);
        
        for (int i=0; i < numPoints; i++) {
            String vec = rvgen.vecToStr(i, o[i].getVec());
            bw.write(vec);
        }
        bw.close();
        fw.close();
    }
    
    int loadRandomSamples() throws Exception {
        List<String> vecLines = FileUtils.readLines(new File(rvgen.randomSamplesFileName()));
        int i=0;
        for (String vecLine : vecLines) {
            o[i++] = new FloatByteRcd(i, rvgen.strToVec(vecLine));
        }
        return i;
    }
    
    public void indexAll() throws Exception {
        if (!syntheticQueries)
            this.startIdOfRef = loadFile(prop.getProperty("dvec.file"), this.o);
        else {
            this.startIdOfRef = loadRandomSamples();
            
        }
        
        chooseRandomRef();
        computeDistRanksFromRef();
        save();
    }
    
    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (int i=0; i < numRefPoints; i++) {
            buff.append(rinfos[i].toString()).append("\n");
        }
        return buff.toString();
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java MIndexer <prop-file>");
            args[0] = "init_synthetic.properties";
        }
        
        try {
            MIndexer mindexer = new MIndexer(args[0]);
            mindexer.indexAll();
            System.out.println(mindexer.o[0]);
            
            // Load the saved model...
            MIndexer loaded = MIndexer.load(args[0]);
            //System.out.println(loaded.toString());            
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}
