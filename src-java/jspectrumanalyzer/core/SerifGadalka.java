package jspectrumanalyzer.core;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class SerifGadalka {
@SerializedName("azimuth")
private int azimuth;

@SerializedName("frequency")
private FrequencyRangeGadalka frequencyRange;

public SerifGadalka(int azimuth, FrequencyRangeGadalka frequencyRange) {
    this.azimuth = azimuth;
    this.frequencyRange = frequencyRange;
}

public int getAzimuth() {
    return azimuth;
}

public void setAzimuth(int azimuth) {
    this.azimuth = azimuth;
}

public FrequencyRangeGadalka getFrequencyRange() {
    return frequencyRange;
}

public void setFrequencyRange(FrequencyRangeGadalka frequencyRange) {
    this.frequencyRange = frequencyRange;
}

@Override
public String toString() {
    Gson gson = new Gson();
    return gson.toJson(this);
}
}
