package jspectrumanalyzer.core;

public class AzimuthDbm {

    private final int azimuth; // Азимут
    private final float dbm;  // Значение dBm

    public AzimuthDbm(int azimuth, float dbm) {
        this.azimuth = azimuth;
        this.dbm = dbm;
    }

    public int getAzimuth() {
        return azimuth;
    }

    public float getDbm() {
        return dbm;
    }
}
