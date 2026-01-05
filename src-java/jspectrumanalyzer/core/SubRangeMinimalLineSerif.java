package jspectrumanalyzer.core;

public class SubRangeMinimalLineSerif {
    private float startFreq;
    private float endFreq;
    private int dbLine;

    public SubRangeMinimalLineSerif(float startFreq, float endFreq, int dbLine){
        this.startFreq = startFreq;
        this.endFreq = endFreq;
        this.dbLine = dbLine;
    }

    public int getDbLine() {
        return dbLine;
    }

    public void setDbLine(int dbLine) {
        this.dbLine = dbLine;
    }

    public float getEndFreq() {
        return endFreq;
    }

    public float getStartFreq() {
        return startFreq;
    }
}
