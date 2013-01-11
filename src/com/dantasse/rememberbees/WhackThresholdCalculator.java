package com.dantasse.rememberbees;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.util.Pair;

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
    public float determineWhackThreshold(float[] data) {
        float[] dataClone = data.clone();
        int numToThrowOut = dataClone.length / 100;
        List<Float> highests = new ArrayList<Float>();
        while (highests.size() < RememberBeesActivity.NUM_CALIBRATION_WHACKS) {
            float max = 0.0f;
            int maxIndex = 0;
            for(int i = 0; i < dataClone.length; i++) {
                if (dataClone[i] > max) {
                    max = dataClone[i];
                    maxIndex = i;
                }
            }
            highests.add(max);
            
            // throw out all data within a couple readings of that point, so we don't get one long
            // hit being counted as all of the calibration whacks.
            for (int i = maxIndex - numToThrowOut; i <= maxIndex + numToThrowOut; i++) {
                if (i >= 0 && i < dataClone.length) {
                  dataClone[i] = Float.NEGATIVE_INFINITY;
                }
            }
        }

        float mean = mean(highests);//sum / lowests.size();
        float sd = sd(highests);
        return mean - 2*sd; // 2 sd less than mean
    }
    
    /** Returns the 10 highest values from |readings| and their indices in |readings| */
    public List<Pair<Float, Integer>> findHighests(List<Float> readings) {

        // for each one it's a reading (float) and an index into |readings| (integer)
        List<Pair<Float, Integer>> highests = new ArrayList<Pair<Float, Integer>>();
        for(int i = 0; i < 10; i++)
            highests.add(new Pair<Float, Integer>(0.0f, 0));
        
        for (int i = 0; i < readings.size(); i++) {
            Float f = readings.get(i);

            if (f > highests.get(0).first) {
                highests.remove(0);
                highests.add(new Pair<Float, Integer>(f, i));
                Collections.sort(highests, new Comparator<Pair<Float, Integer>>() {
                    @Override
                    public int compare(Pair<Float, Integer> lhs, Pair<Float, Integer> rhs) {
                        return lhs.first.compareTo(rhs.first);
                    }
                });
            }
        }
        return highests;
    }
}
