package jspectrumanalyzer.core;

public class FrequencyRangeDivider {
    private final int startFreq;
    private final int endFreq;
    private final float averageDbm;

    public FrequencyRangeDivider(int startFreq, int endFreq, float averageDbm) {
        this.startFreq = startFreq;
        this.endFreq = endFreq;
        this.averageDbm = averageDbm;
    }

    public int getStartFreq() {
        return startFreq;
    }

    public int getEndFreq() {
        return endFreq;
    }

    public double getAverageDbm() {
        return averageDbm;
    }
}
