/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit5TestClass.java to edit this template
 */
package esa.mo.nmf.apps;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


/**
 * JUnit test class for Histogram class.
 * @author Nora Silven
 */
public class HistogramTest {
    
    /**
     * Test of update method, of class Histogram.
     */
    @Test
    public void testUpdate() {
        Random rnd = new Random();
        int minLength = 10;
        double binWidth = 0.25;
        
        for (int i = 0; i < 100; i++) {
            int addedLength = rnd.nextInt(50);
            int l = minLength + addedLength;
            double[] testCase = new double[l];
            double min = Double.MAX_VALUE;
            
            for (int j = 0; j < l; j++) {
                double v = rnd.nextGaussian();
                testCase[j] = v;
                if (v < min)
                    min = v;
            }
            
            Histogram h = new Histogram(binWidth, min);
            for (double v : testCase) {
                int bin = (int) ((v - min) / binWidth);
                
                HashMap<Integer, Integer> hOld = h.getHist();
                int oldv = 0;
                if (hOld.containsKey(bin))
                    oldv = hOld.get(bin);
                
                h = h.update(v);
                HashMap<Integer, Integer> hNew = h.getHist();
                int newv = hNew.get(bin);
                
                int diff = newv - oldv;
                assertEquals(1, diff);
            }
            
            HashMap<Integer, Integer> hTotal = h.getHist();
            int sum = 0;
            for (int v : hTotal.values())
                sum += v;
            assertEquals(sum, l);
        }
    }
    

    /**
     * Test of predictProb method, of class Histogram.
     */
    @Test
    public void testPredictProb() {
        Random rnd = new Random();
        int minLength = 10;
        double binWidth = 0.25;
        
        for (int i = 0; i < 100; i++) {
            int addedLength = rnd.nextInt(50);
            int l = minLength + addedLength;
            double[] testCase = new double[l];
            double min = Double.MAX_VALUE;
            
            for (int j = 0; j < l; j++) {
                double v = rnd.nextGaussian();
                testCase[j] = v;
                if (v < min)
                    min = v;
            }
            
            Histogram h = new Histogram(binWidth, min);
            for (double v : testCase)
                h = h.update(v);
            
            HashMap<Integer, Double> pdf = h.getPdf();
            
            double sum = 0;
            for (double v : pdf.values())
                sum += v;
            
            assertEquals(1.0, sum * binWidth, 0.000001);
        }
    }
}
