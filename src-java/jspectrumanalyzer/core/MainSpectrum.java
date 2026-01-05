package jspectrumanalyzer.core;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class MainSpectrum {
    @SerializedName("hackrfuuid")
    private String hackrfuuid;

    @SerializedName("massdbm")
    private float[] massdbm;

    public MainSpectrum(String hackrfuuid, float[] massdbm) {
        this.hackrfuuid = hackrfuuid;
        this.massdbm = massdbm;
    }


    @Override
    public String toString() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}
