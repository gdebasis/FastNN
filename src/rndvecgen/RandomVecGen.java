/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rndvecgen;
import indexer.DocVector;
import java.io.*;
import java.util.*;
import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.distribution.MixtureMultivariateNormalDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.Pair;

/**
 *
 * @author Debasis
 */
public class RandomVecGen {
    
    Properties prop;
    int numPoints;
    int numGaussians;
    int numDimensions;
    int maxSpan;
    float min;
    float max;
    boolean diagonalCovMatrix;
    
    final int SEED = 11781;
    
    NormalDistribution[] muGen;
    MixtureMultivariateNormalDistribution mixtureDist;
    
    public RandomVecGen(Properties prop) throws Exception {
        this.prop = prop;
        init();
    }
    
    public int getNumDimensions() { return numDimensions; }
    public float getMin() { return min; }
    public float getMax() { return max; }
    public int getNumPoints() { return numPoints; }
    
    private void init() throws Exception {
        DocVector.initVectorRange(prop);
        
        numPoints = Integer.parseInt(prop.getProperty("syntheticdata.numsamples"));
        numGaussians = Integer.parseInt(prop.getProperty("syntheticdata.numgaussians"));
        maxSpan = Integer.parseInt(prop.getProperty("syntheticdata.maxSpan"));
        diagonalCovMatrix = Boolean.parseBoolean(prop.getProperty("syntheticdata.diagonalcov"));
        
        min = Float.parseFloat(prop.getProperty("syntheticdata.min"));
        max = Float.parseFloat(prop.getProperty("syntheticdata.max"));
                
        numDimensions = Integer.parseInt(prop.getProperty("vec.numdimensions"));
        
        final int NUM_MU_GEN = 5;
        float delta = (max - min)/NUM_MU_GEN;
        muGen = new NormalDistribution[NUM_MU_GEN];
        for (int i=0; i < NUM_MU_GEN; i++) {
            muGen[i] = new NormalDistribution(min+i*delta, Math.random()*maxSpan);
        }
        
        
        List<Pair<Double,MultivariateNormalDistribution>> components = new ArrayList<>();
        for (int i=0; i < numGaussians; i++) {
            components.add(new Pair(new Double(1/(double)numGaussians), genRandom(i)));
        }
        
        // Ensure that we can reproduce the results...
        RandomGenerator rg = new JDKRandomGenerator();
        rg.setSeed(SEED);
        this.mixtureDist = new MixtureMultivariateNormalDistribution(rg, components);                
    }
    
    public RandomVecGen(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));        
        init();
    }
    
    double[] sample() {
        // choose a component at random
        int componentId = (int)(Math.random()*numGaussians);
        return mixtureDist.getComponents().get(componentId).getSecond().sample();
    }
    
    double[] initRandomMean() {
        
        double[] means = new double[numDimensions];
        for (int i=0; i < numDimensions; i++) {
            //double x = min + Math.random()*(max - min);            
            double x = muGen[(int)(Math.random()*muGen.length)].sample();
            means[i] = x;
        }
        return means;
    }
    
    double[][] initRandomCovMatrix(int maxSpan) {
        double[][] cov = new double[numDimensions][numDimensions];
        for (int i=0; i < numDimensions; i++) {
            for (int j=0; j < numDimensions; j++) {
                double x = 0.1 + Math.random()*(maxSpan-1);
                cov[i][j] = x;
            }
        }
        return cov;
    }

    double[][] initRandomCovMatrixDiagonal(int maxSpan) {
        double[][] cov = new double[numDimensions][numDimensions];
        for (int i=0; i < numDimensions; i++) {
            double x = 0.1 + Math.random()*(maxSpan-1);
            cov[i][i] = x;
        }
        return cov;
    }
    
    MultivariateNormalDistribution genRandom(int id) {
        double[] meanvec = initRandomMean();
        //System.out.println("Mean vec: " + vecToStr(id, meanvec));
        double[][] cov = diagonalCovMatrix? initRandomCovMatrixDiagonal(maxSpan) : initRandomCovMatrix(maxSpan);
        MultivariateNormalDistribution dist = new MultivariateNormalDistribution(meanvec, cov);
        return dist;
    }

    public float[] strToVec(String line) {
        String[] tokens = line.split("\\s+");
        int ndimensions = tokens.length-1;
        float[] vec = new float[ndimensions];
        for (int i=0; i < vec.length; i++)
            vec[i] = Float.parseFloat(tokens[i+1]);
        return vec;
    }
    
    public String vecToStr(int id, double[] vec) {
        StringBuffer buff = new StringBuffer();
        buff.append(id).append("\t");
        for (int i=0; i < vec.length; i++) {
            buff.append((float)vec[i]).append(" ");
        }
        buff.append("\n");
        return buff.toString();
    }

    public String vecToStr(int id, float[] vec) {
        StringBuffer buff = new StringBuffer();
        buff.append(id).append("\t");
        for (int i=0; i < vec.length; i++) {
            buff.append(vec[i]).append(" ");
        }
        buff.append("\n");
        return buff.toString();
    }
    
    public String randomSamplesFileName() {
        String fileName = prop.getProperty("syntheticdata.outdir") + "/" + 
                "data." + numGaussians + "." + maxSpan + "." + numDimensions + ".txt";
        return fileName;
        
    }
    public void generateSamples() throws Exception {        
        FileWriter fw = new FileWriter(randomSamplesFileName());
        BufferedWriter bw = new BufferedWriter(fw);
        
        System.out.println("Generating random samples...");
        for (int i=0; i < numPoints; i++) {
            //bw.write(vecToStr(i, this.mixtureDist.sample()));
            String str = vecToStr(i, sample());
            bw.write(str);
            //System.out.println(str);
        }
        
        bw.close();
        fw.close();        
    }
    
    public MixtureMultivariateNormalDistribution getMixDist() { return this.mixtureDist; }
        
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java RandomVecGen <prop-file>");
            args[0] = "init_synthetic.properties";
        }
        
        try {
            RandomVecGen rvgen = new RandomVecGen(args[0]);
            rvgen.generateSamples();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
}
