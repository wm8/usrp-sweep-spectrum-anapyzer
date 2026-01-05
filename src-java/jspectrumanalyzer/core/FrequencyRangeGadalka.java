package jspectrumanalyzer.core;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class FrequencyRangeGadalka {
    @SerializedName("min")
    private int minFrequency;

    @SerializedName("max")
    private int maxFrequency;

    public FrequencyRangeGadalka(int minFrequency, int maxFrequency) {
        this.minFrequency = minFrequency;
        this.maxFrequency = maxFrequency;
    }

    public int getMinFrequency() {
        return minFrequency;
    }

    public void setMinFrequency(int minFrequency) {
        this.minFrequency = minFrequency;
    }

    public int getMaxFrequency() {
        return maxFrequency;
    }

    public void setMaxFrequency(int maxFrequency) {
        this.maxFrequency = maxFrequency;
    }

    @Override
    public String toString() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}