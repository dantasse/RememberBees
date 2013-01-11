package com.dantasse.rememberbees;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class TestRememberBeesActivity {

    float[] data;
    WhackThresholdCalculator wtc;
    
    @Before
    public void setUp() {
        wtc = new WhackThresholdCalculator();
        data = new float[500];
        for (int i = 0; i < data.length; i++) {
            data[i] = 0;
        }        
    }
    
    @Test
    public void testDetermineWhackThresholdWorksWithSimpleData() {
        data[5] = 5;
        data[50] = 5;
        data[200] = 5;
        data[300] = 5;
        data[400] = 5;
        data[450] = 5;
        
        // SD of 6 highests = 0, mean = 5, so threshold = 5.
        assertEquals(5, wtc.determineWhackThreshold(data), .01);

    }
    
    @Test
    public void testDetermineWhackThresholdDoesntChangeArray() {
        data[10] = -12;
        float[] dataBefore = data.clone();
        assertArrayEquals(dataBefore, data, .01f);
    }
}
