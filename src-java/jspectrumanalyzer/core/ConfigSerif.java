package jspectrumanalyzer.core;

import java.util.List;

public class ConfigSerif {

    private Integer currentStartFrequency;
    private Integer currentEndFrequency;
    private Integer frequencyStep;
    private Integer subRangeStep;
    private List<SubRangeMinimalLineSerif> minDbLine;
    private Integer aboveThresholdLimit;
    private Integer belowThresholdLimit;
    private List<RangeForSerif> rangeForSerifs;

    public ConfigSerif(Integer currentStartFrequency,
                       Integer currentEndFrequency,
                       Integer frequencyStep,
                       Integer subRangeStep,
                       List<SubRangeMinimalLineSerif> minDbLine,
                       Integer aboveThresholdLimit,
                       Integer belowThresholdLimit,
                       List<RangeForSerif> rangeForSerifs){
        this.currentStartFrequency = currentStartFrequency;
        this.currentEndFrequency = currentEndFrequency;
        this.frequencyStep = frequencyStep;
        this.subRangeStep = subRangeStep;
        this.minDbLine = minDbLine;
        this.aboveThresholdLimit = aboveThresholdLimit;
        this.belowThresholdLimit = belowThresholdLimit;
        this.rangeForSerifs = rangeForSerifs;
    }

    public void setCurrentStartFrequency(Integer currentStartFrequency) {
        this.currentStartFrequency = currentStartFrequency;
    }

    public void setCurrentEndFrequency(Integer currentEndFrequency) {
        this.currentEndFrequency = currentEndFrequency;
    }

    public void setFrequencyStep(Integer frequencyStep) {
        this.frequencyStep = frequencyStep;
    }

    public void setSubRangeStep(Integer subRangeStep) {
        this.subRangeStep = subRangeStep;
    }

    public void setAboveThresholdLimit(Integer aboveThresholdLimit) {
        this.aboveThresholdLimit = aboveThresholdLimit;
    }

    public void setMinDbLine(List<SubRangeMinimalLineSerif> minDbLine) {
        this.minDbLine = minDbLine;
    }

    public void setBelowThresholdLimit(Integer belowThresholdLimit) {
        this.belowThresholdLimit = belowThresholdLimit;
    }

    public void setRangeForSerifs(List<RangeForSerif> rangeForSerifs) {
        this.rangeForSerifs = rangeForSerifs;
    }

    public Integer getCurrentStartFrequency() {
        return currentStartFrequency;
    }

    public Integer getFrequencyStep() {
        return frequencyStep;
    }

    public Integer getCurrentEndFrequency() {
        return currentEndFrequency;
    }

    public Integer getSubRangeStep() {
        return subRangeStep;
    }

    public List<SubRangeMinimalLineSerif> getMinDbLine() {
        return minDbLine;
    }

    public Integer getAboveThresholdLimit() {
        return aboveThresholdLimit;
    }

    public Integer getBelowThresholdLimit() {
        return belowThresholdLimit;
    }

    public List<RangeForSerif> getRangeForSerifs() {
        return rangeForSerifs;
    }
}
