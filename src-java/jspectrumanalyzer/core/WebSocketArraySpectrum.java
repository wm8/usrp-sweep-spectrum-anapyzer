package jspectrumanalyzer.core;

public class WebSocketArraySpectrum {

    private float[] array;
    private Integer azimuth;

    // Метод для изменения числа
    public void setNumber(float[] array) {
        this.array = array;
    }

    // Метод для получения текущего числа
    public float[] getNumber() {
        return array;
    }

    public void setAzimuth(Integer azimuth) {
        this.azimuth = azimuth;
    }

    public Integer getAzimuth() {return  azimuth;}
}
