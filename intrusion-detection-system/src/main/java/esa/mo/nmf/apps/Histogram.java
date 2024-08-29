/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package esa.mo.nmf.apps;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;


/**
 * Histogram for Loda.
 * @author Nora Silven
 */
public class Histogram {
    
    private final double binWidth;
    private double min;
    private int maxK;
    private int maxs;
    private double histSum;
    private HashMap<Integer, Integer> binCounts;
    private HashMap<Integer, Double> pdf;
    
    
    /**
     * Initialize histogram.
     * @param binWidth widht of all bins
     * @param min minimum value expected in the histogram
     */
    public Histogram(double binWidth, double min) {
        this.binWidth = binWidth;
        this.min = min;
        this.histSum = 0;
        this.binCounts = new HashMap<>();
        this.pdf = new HashMap<>();
    }
    
    
    /**
     * Update histogram by a value.
     * @param x value to update with
     * @return the updated histogram
     */
    public Histogram update(double x) {
        int k = (int) Math.floor((x - this.min) / this.binWidth);
        
        // Calculate left and right bin edges for x.
        double l = k * this.binWidth + this.min;
        double r = (k+1) * this.binWidth + min;
        
        // Check if x actually falls within bin k.
        if (x > r)
            k += 1;
        else if (x < l)
            k -= 1;
        
        int count = 0;
        if (this.binCounts.containsKey(k))
            count = this.binCounts.get(k);
        
        this.binCounts.put(k, count + 1);
        
        this.histSum = this.histSum + this.binWidth;
        
        // Update the estimated pdf.
        for (int key : this.binCounts.keySet()) {
            int c = this.binCounts.get(key);
            this.pdf.put(key, (c / this.histSum));
        }
        
        return this;
    }
    
    
    /**
     * Get likelihood of value.
     * @param x value for which to get likelihood
     * @return likelihood of x
     */
    public double predictProb(double x) {
        int k = (int) Math.floor((x - this.min) / this.binWidth);
        
        // Calculate left and right bin edges for x.
        double l = k * this.binWidth + this.min;
        double r = (k+1) * this.binWidth + this.min;
        
        // Check if x actually falls in bin k.
        if (x > r)
            k += 1;
        else if (x < l)
            k -= 1;
        
        // Get likelihood from pdf, if 0, return minimum representable value instead.
        double prob;
        if (this.pdf.containsKey(k)) {
            prob = this.pdf.get(k);
        } else {
            prob = Double.MIN_VALUE * 2;
        }
        
        return prob;
    }
    
    
    /**
     * Getter for histogram bin values.
     * @return histogram bin values
     */
    public HashMap<Integer, Integer> getHist() {
        return this.binCounts;
    }
    
    
    /**
     * Getter for estimated pdf values.
     * @return estimated pdf values
     */
    public HashMap<Integer, Double> getPdf() {
        return this.pdf;
    }
}
