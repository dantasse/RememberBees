package com.dantasse.rememberbees;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class WhackThresholdCalculator {

    
    private float sum (List<Float> a){
        float sum = 0f;
        for (Float f : a) {
            sum += f;
        }
        return sum;
    }
    
    private float mean (List<Float> a){
        return sum(a) / a.size();
    }

    private float sd (List<Float> a){
        if (a.size() < 2) {
            return 0; //I guess?
        }
        float sum = 0;
        double mean = mean(a);
        for (float f : a) {
            sum += Math.pow((f - mean), 2);
        }
        return (float) Math.sqrt(sum / (a.size() - 1)); // sample
    }

    /**
     * @param calibrationData The accelerometer readings for 5 sec while you're
     *   calibrating. Not modified by this function.
     * @return the value that, if the user hits this hard, it should be counted
     *   as a whack.
     */
    float determineWhackThreshold(float[] data) {
        float[] dataClone = data.clone();
        int numToThrowOut = dataClone.length / 100;
        List<Float> lowests = new ArrayList<Float>();
        while (lowests.size() < RememberBeesActivity.NUM_CALIBRATION_WHACKS) {
            float min = 0.0f;
            int minIndex = 0;
            for(int i = 0; i < dataClone.length; i++) {
                if (dataClone[i] < min) {
                    min = dataClone[i];
                    minIndex = i;
                }
            }
            lowests.add(min);
            
            // throw out all data within a couple readings of that point, so we don't get one long
            // hit being counted as all of the calibration whacks.
            for (int i = minIndex - numToThrowOut; i <= minIndex + numToThrowOut; i++) {
                if (i >= 0 && i < dataClone.length) {
                  dataClone[i] = Float.MAX_VALUE;
                }
            }
        }

        float mean = mean(lowests);//sum / lowests.size();
        float sd = sd(lowests);
        Logger.getAnonymousLogger().warning("mean: " + mean + ", sd: " + sd(lowests));
        return mean + 2 * sd; // plus because we're all in the negatives here...
    }
}
