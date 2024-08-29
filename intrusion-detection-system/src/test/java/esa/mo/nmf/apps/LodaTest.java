/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit5TestClass.java to edit this template
 */
package esa.mo.nmf.apps;

import java.util.Random;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author nora
 */
public class LodaTest {

    /**
     * Test of initProjections method, of class Loda.
     */
    @Test
    public void testProjections() {
        Random rnd = new Random();
        
        for (int i = 0; i < 10; i++) {
            int nEstimators = 100 + rnd.nextInt(75);
            float q = rnd.nextFloat();
            int samples = 100 + rnd.nextInt(200);
            int features = 3 + rnd.nextInt(8);
            double[][] init = new double[samples][features];
            
            for (int j = 0; j < samples; j++) {
                for (int k = 0; k < features; k++) {
                    init[j][k] = rnd.nextGaussian();
                }
            }
            
            Loda loda = new Loda(nEstimators, q, init);
            double[][] p = loda.getProjections();
            
            assertEquals(nEstimators, p.length);
            assertEquals(features, p[0].length);
            
            int nonzero = (int) Math.round(Math.sqrt(features));
            for (double[] pv : p) {
                int nz = 0;
                for (int j = 0; j < features; j++)
                    if (pv[j] != 0) nz++;
                
                assertEquals(nonzero, nz);
            }
        }
    }

    
    /**
     * Test of switchHists method, of class Loda.
     */
    @Test
    public void testSwitchHists() {
        Random rnd = new Random();
        
        for (int i = 0; i < 10; i++) {
            int nEstimators = 100 + rnd.nextInt(75);
            float q = rnd.nextFloat();
            int samples = 100 + rnd.nextInt(200);
            int features = 3 + rnd.nextInt(8);
            double[][] init = new double[samples][features];
            
            for (int j = 0; j < samples; j++) {
                for (int k = 0; k < features; k++) {
                    init[j][k] = rnd.nextGaussian();
                }
            }
            
            Loda loda = new Loda(nEstimators, q, init);
            Histogram[] hists = loda.getHists();
            
            for (Histogram h : hists)
                assertEquals(null, h);
        }
    }

    /**
     * Test of anomaly quantile, of class Loda.
     */
    @Test
    public void testQuantile() {
        Random rnd = new Random();
        
        for (int i = 0; i < 10; i++) {
            int nEstimators = 100 + rnd.nextInt(75);
            float q = rnd.nextFloat();
            int samples = 100 + rnd.nextInt(200);
            int features = 3 + rnd.nextInt(8);
            double[][] init = new double[samples][features];
            
            for (int j = 0; j < samples; j++) {
                for (int k = 0; k < features; k++) {
                    init[j][k] = rnd.nextGaussian();
                }
            }
            
            Loda loda = new Loda(nEstimators, q, init);
            
            int anomalies = 0;
            for (double[] s : init) {
                int p = loda.predict(s);
                if (p == -1) anomalies++;
            }
            
            assertEquals(q, (double) anomalies / samples, 0.15);
        }
    }
}
