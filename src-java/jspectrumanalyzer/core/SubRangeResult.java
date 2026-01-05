package jspectrumanalyzer.core;

public class SubRangeResult {
    public final long startFreq;
    public final long endFreq;
    public float maxDbm;
    public int currentAzimuth;
    public int aboveThresholdCount = 0;
    public int belowThresholdCount = 0;

    public SubRangeResult(long startFreq, long endFreq, float maxDbm, int currentAzimuth) {
        this.startFreq = startFreq;
        this.endFreq = endFreq;
        this.maxDbm = maxDbm;
        this.currentAzimuth = currentAzimuth;
    }

    public int getStartFreq() {
        return (int) (startFreq / 1000000);
    }

    public int getEndFreq() {
        return (int) (endFreq / 1000000);
    }

    public float getMaxDbm() {
        return maxDbm;
    }

    public  int getCurrentAzimuth(){
        return currentAzimuth;
    }

    @Override
    public String toString() {
        return "Диапазон: " + startFreq / 1000000 + "-" + endFreq / 1000000 + " МГц, Макс dBm: " + maxDbm + " текущий азимут: "+ currentAzimuth ;
    }
}