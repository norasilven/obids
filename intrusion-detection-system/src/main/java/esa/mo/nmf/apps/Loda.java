/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package esa.mo.nmf.apps;

import java.util.Arrays;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.jblas.DoubleMatrix;

/**
 * The Lightweight Online Detector of Anomalies algorithm.
 * @author Nora Silven
 */
public class Loda {
    
    private final int nEstimators;
    private final float q;
    private final int randomState;
    private int windowSize;
    private int nFeatures;
    private DoubleMatrix projections;
    private Histogram[] hists;
    private Histogram[] estimators;
    private double[] xScores;
    private double anomalyThreshold;
    private double[] binWidths;
    private double[] mins;
    private int sampleCount;
    
    
    /**
     * Set the hyperparameters of Loda.
     * @param nEstimators number of histograms
     * @param q quantile of anomaly ratio
     * @param randomState seed
     * @param x initial window of samples
     */
    public Loda(int nEstimators, float q, int randomState, double[][] x) {
        this.nEstimators = nEstimators;
        this.q = q;
        this.randomState = randomState;
        this.validate();
        this.initiateLoda(x);
    }
    
    
    /**
     * Set the hyperparameters of Loda.
     * @param nEstimators number of histograms
     * @param q quantile of anomaly ratio
     * @param x initial window of samples
     */
    public Loda(int nEstimators, float q, double[][] x) {
        this.nEstimators = nEstimators;
        this.q = q;
        this.randomState = 0;
        this.validate();
        this.initiateLoda(x);
    }
    
    
    /**
     * Initialize Loda with standard hyperparameters.
     * @param x initial window of samples
     */
    public Loda(double[][] x) {
        this.nEstimators = 140;
        this.q = 0.05f;
        this.randomState = 0;
        this.validate();
        this.initiateLoda(x);
    }
    
    
    /**
     * Initialize Loga with the first window of samples.
     * @param x initial window of samples
     */
    private void initiateLoda(double[][] x) {
        // Initialize all necessary variables.
        this.sampleCount = 0;
        this.windowSize = x.length;
        this.nFeatures = x[0].length;
        this.projections = this.initProjections();
        this.binWidths = this.optimizeBins(x);
        this.hists = new Histogram[this.nEstimators];
        this.estimators = new Histogram[this.nEstimators];
        this.xScores = new double[this.windowSize];
        
        // Build the first set of classification histograms.
        for (double[] sample : x) 
            this.update(sample);
    }
    
    
    /**
     * Validate the hyperparameters.
     * @throws IllegalArgumentException when a negative number of histograms is given, or when a
     * anomaly ratio outside the [0, 1] range is given.
     */
    private void validate() throws IllegalArgumentException  {
        if (this.nEstimators < 0)
            throw new IllegalArgumentException("Loda: n_estimators must be positive.");
        
        if (this.q < 0 || this.q > 1)
            throw new IllegalArgumentException("Loda: q must be in [0, 1].");
    }
    
    
    /**
     * Initialize random sparse projection vectors.
     * @return random sparse projection vectors
     */
    private DoubleMatrix initProjections() {
        int nonZeroW = (int) Math.round(Math.sqrt(this.nFeatures));
        RandomGenerator rnd;
        
        if (this.randomState == 0)
            rnd = new MersenneTwister();
        else
            rnd = new MersenneTwister(this.randomState);
        
        // Create vectors with random numbers pulled from U(0, 1).
        DoubleMatrix randArr = new DoubleMatrix(this.nEstimators, this.nFeatures);
        for (int i = 0; i < this.nEstimators; i++) {
            for (int j = 0; j < this.nFeatures; j++)
                randArr = randArr.put(i, j, rnd.nextDouble());
        }
        
        // For each vector, the indices at which the uniform random values are smallest, random normal
        // values are inserted into the projection vector.
        DoubleMatrix p = new DoubleMatrix(this.nEstimators, this.nFeatures);
        for (int i = 0; i < this.nEstimators; i++) {
            for (int j = 0; j < nonZeroW; j++) {
                int m = randArr.rowArgmins()[i];
                randArr.put(i, m, 1.0);
                p.put(i, m, rnd.nextGaussian());
            }
        }
                
        return p;
    }
    
    
    /**
     * Helper function to calculate a quantile of a list of numbers.
     * @param data the list of numbers
     * @param quantile the quantile
     * @return the quantile of the list of numbers
     */
    private static double calculateQuantile(double[] data, double quantile) {
        Arrays.sort(data);
        double n = quantile * (data.length - 1) + 1;
        if (n == 1) 
            return data[0];
        else if (n == data.length) 
            return data[data.length - 1];
        else {
            int k = (int) n;
            double d = n - k;
            return data[k - 1] + d * (data[k] - data[k - 1]);
        }
    }
    
    
    /**
     * Update routine of Loda.
     * @param x incoming sample
     * @return the updated model
     */
    public Loda update(double[] x) {
        this.nFeatures = x.length;
        
        // Project the sample.
        DoubleMatrix rawData = new DoubleMatrix(x);
        double[] wX = rawData.transpose().mmul(this.projections.transpose()).data;
        double[] xProb = new double[this.nEstimators];
        
        // Update each histogram with the projected sample.
        for (int i = 0; i < this.nEstimators; i++) {
            double wx = wX[i];
            
            if (this.hists[i] == null) {
                Histogram newHist = new Histogram(this.binWidths[i], this.mins[i]);
                this.hists[i] = newHist;
            }
            
            this.hists[i] = this.hists[i].update(wx);
            double prob = this.hists[i].predictProb(wx);
            xProb[i] = Math.log(prob);
        }
        
        // Store the anomaly score of the sample, to later calculate the anomaly threshold.
        double xScore = new DoubleMatrix(xProb).mean();
        this.xScores[this.sampleCount] = xScore;
        this.sampleCount++;
        
        // If a window of samples has passed, switch the classification and training histograms.
        if (this.sampleCount == this.windowSize)
            this.switchHists();
        
        return this;
    }
    
    
    /**
     * Calculate the log-likelihoods from all histograms for a sample.
     * @param x a data sample
     * @return the log-likelihoods for all histograms
     */
    private double[] logProb(double[] x) {
        if ((this.estimators == null) || (this.projections == null))
            throw new RuntimeException("Estimator is not yet fitted.");
        
        // Project the sample.
        DoubleMatrix rawData = new DoubleMatrix(x);
        double[] wX = rawData.transpose().mmul(this.projections.transpose()).data;
        double[] xProb = new double[this.nEstimators];
        
        // Calculate log-likelihoods.
        for (int i = 0; i < this.nEstimators; i++) {
            double wx = wX[i];
            Histogram curHist = this.estimators[i];
            double prob = curHist.predictProb(wx);
            xProb[i] = Math.log(prob);
        }
        
        return xProb;
    }
    
    
    /**
     * Calculate the anomaly score of a sample.
     * @param x a data sample
     * @return the anomaly score
     */
    public double scoreSample(double[] x) {
        double[] xLogProb = this.logProb(x);
        double xScore = new DoubleMatrix(xLogProb).mean();
        return xScore;
    }
    
    
    /**
     * Classify a sample.
     * @param x a data sample
     * @return the class of the sample (-1=anomalous, 1=normal)
     */
    public int predict(double[] x) {
        if (this.anomalyThreshold == 0.0f)
            throw new RuntimeException("Estimator is not yet fitted.");
        
        double score = this.scoreSample(x);
        
        if (score < this.anomalyThreshold)
            return -1;
        else
            return 1;
    }
    
    
    /**
     * Calculate the variance of a list of numbers.
     * @param a a list of numbers
     * @return the variance
     */
    private static double var(double[] a) {
        double mean = new DoubleMatrix(a).mean();
        double var = 0;
        
        for (int i = 0; i < a.length; i++) {
            var += Math.pow(a[i] - mean, 2);
        }
        
        var /= a.length;
        
        return var;
    }
    
    
    /**
     * Score the features in a sample based on their contribution to the anomalousness.
     * @param x a data sample
     * @return the scores for all features
     */
    public double[] scoreFeatures(double[] x) {
        double[] xLogProb = this.logProb(x);
        double[] xNegLogProb = new DoubleMatrix(xLogProb).neg().data;
        double[] result = new double[this.nFeatures];
        
        for (int i = 0; i < this.nFeatures; i++) {
            boolean[] nonZeroP = this.projections.getColumn(i).toBooleanArray();
            
            int withRow = 0;
            int woRow = 0;
            
            for (boolean b : nonZeroP) {
                if (b) withRow++;
                else woRow++;
            }
            
            double[] iWithFeature = new double[withRow];
            double[] iWoFeature = new double[woRow];
            
            withRow = 0;
            woRow = 0;
            for (int j = 0; j < this.nEstimators; j++) {
                boolean isNotZero = nonZeroP[j];
                
                if (isNotZero) {
                    iWithFeature[withRow] = xNegLogProb[j];
                    withRow++;
                } else {
                    iWoFeature[woRow] = xNegLogProb[j];
                    woRow++;
                }
            }
            
            double withMean = new DoubleMatrix(iWithFeature).mean();
            double woMean = new DoubleMatrix(iWoFeature).mean();
            double meanDiff = withMean - woMean;
            
            double withVar = var(iWithFeature) / iWithFeature.length;
            double woVar = var(iWoFeature) / iWoFeature.length;
            double sqrtVarSum = Math.sqrt(withVar + woVar);
            
            result[i] = meanDiff / sqrtVarSum;
        }
        
        return result;
    }
    
    
    /**
     * Determine the optimal bin widths based on the estimated range of values.
     * @param x initial window of samples
     * @return the optimal bin widths for all histograms
     */
    private double[] optimizeBins(double[][] x) {
        DoubleMatrix rawData = new DoubleMatrix(x);
        DoubleMatrix wX = rawData.mmul(this.projections.transpose());
        DoubleMatrix mins = wX.columnMins();
        this.mins = mins.data;
        DoubleMatrix maxs = wX.columnMaxs();
        
        int b = 1;
        
        int[] bins = new int[this.nEstimators];
        double[] maxL = new double[this.nEstimators];
        double[] maxWidths = new double[this.nEstimators];
        
        for (int i = 0; i < this.nEstimators; i++) maxL[i] = -(Double.MAX_VALUE / 2);
        
        while (true) {
            DoubleMatrix ranges = maxs.sub(mins);
            double[] bw = ranges.div(b).data;
            
            for (int i = 0; i < this.nEstimators; i++) {
                Histogram hist = new Histogram(bw[i], this.mins[i]);
                
                for (int j = 0; j < this.windowSize; j++) {
                    double v = wX.get(j, i);
                    hist = hist.update(v);
                }
                
                double sum = 0;
                for (int v : hist.getHist().values()) {
                    sum += v * Math.log10((double) (b*v) / this.windowSize);
                }
                
                double pen = b - 1 + Math.pow(Math.log10(b), 2.5);
                double l = sum - pen;
                
                if (l > maxL[i]) {
                    bins[i] = b;
                    maxL[i] = l;
                    maxWidths[i] = bw[i];
                }
            }
            
            if (b >= (this.windowSize / Math.log10(this.windowSize))) {
                break;
            }
            
            b++;
        }
        
        return maxWidths;
    }
    
    
    /**
     * Switch out the classification histograms.
     */
    public void switchHists() {
        for (int i = 0; i < this.nEstimators; i++) {
            this.estimators[i] = this.hists[i];
            this.hists[i] = null;
        }
        
        this.anomalyThreshold = calculateQuantile(this.xScores, this.q);
        // System.out.println(String.format("Anomaly Threshold: %.3f", this.anomalyThreshold));
        
        this.xScores = new double[this.windowSize];
        this.sampleCount = 0;
    }
    
    
    /**
     * Getter for the random projection vectors.
     * @return random projection vectors
     */
    public double[][] getProjections() {
        return this.projections.toArray2();
    }
    
    
    /**
     * Getter for training histograms.
     * @return the training histograms
     */
    public Histogram[] getHists() {
        return this.hists;
    }
}
