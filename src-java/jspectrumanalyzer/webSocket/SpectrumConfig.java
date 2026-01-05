package jspectrumanalyzer.webSocket;

public class SpectrumConfig {


    private Integer startFreq;
    private Integer endFreq;
    private boolean antennaLNA;
    private Integer gainLNA;
    private Integer gainVGA;
    private Integer startPalette;
    private Integer sizePalette;
    private Integer samples;
    private Integer fftBins;
    private Integer counterSignal;
    private Integer loseCounter;
    private  Integer subrange;
    private boolean antennapolarization;

    /**
     * Публичный конструктор для инициализации всех полей.
     *
     * @param startFreq    Начальная частота.
     * @param endFreq      Конечная частота.
     * @param antennaLNA   Флаг для антенны LNA.
     * @param gainLNA      Усиление LNA.
     * @param gainVGA      Усиление VGA.
     * @param startPalette Начальное значение палитры.
     * @param sizePalette  Размер палитры.
     * @param samples      Количество сэмплов.
     * @param fftBins      Количество FFT-бинов.
     */
    public SpectrumConfig(
            Integer startFreq,
            Integer endFreq,
            boolean antennaLNA,
            Integer gainLNA,
            Integer gainVGA,
            Integer startPalette,
            Integer sizePalette,
            Integer samples,
            Integer fftBins,
            Integer counterSignal,
            Integer loseCounter,
            Integer subrange,
            boolean antennapolarization) {
        this.startFreq = startFreq;
        this.endFreq = endFreq;
        this.antennaLNA = antennaLNA;
        this.gainLNA = gainLNA;
        this.gainVGA = gainVGA;
        this.startPalette = startPalette;
        this.sizePalette = sizePalette;
        this.samples = samples;
        this.fftBins = fftBins;
        this.counterSignal = counterSignal;
        this.loseCounter = loseCounter;
        this.subrange = subrange;
        this.antennapolarization = antennapolarization;
    }


    public Integer getStartFreq() {
        return startFreq;
    }

    public Integer getEndFreq() {
        return endFreq;
    }


    public boolean isAntennaLNA() {
        return antennaLNA;
    }

    public Integer getGainLNA() {
        return gainLNA;
    }

    public Integer getGainVGA() {
        return gainVGA;
    }

    public Integer getStartPalette() {
        return startPalette;
    }

    public void setStartPalette(Integer startPalette) {
        this.startPalette = startPalette;
    }

    public Integer getSizePalette() {
        return sizePalette;
    }

    public Integer getSamples(){
        return samples;
    }

    public Integer getFftBins(){
        return fftBins;
    }

    public Integer getCounterSignal(){
        return counterSignal;
    }

    public Integer getLoseCounter(){
        return loseCounter;
    }

    public Integer getSubrange() {
        return subrange;
    }

    public boolean getAntennaPolarization() {
        return antennapolarization;
    }
}