package jspectrumanalyzer.core;

public class SignalData {
    public final float[] dbmValues;
    public final int azimuthValue;

    public SignalData(float[] dbmValues, int azimuthValue) {
        this.dbmValues = dbmValues;
        this.azimuthValue = azimuthValue;
    }
}
