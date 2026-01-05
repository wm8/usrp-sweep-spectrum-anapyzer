package jspectrumanalyzer.core;

public class RangeForSerif {
    private Double startX;
    private Double endX;
    private String status;

    public RangeForSerif(Double startX, Double endX, String status) {
        this.startX = startX;
        this.endX = endX;
        this.status = status;
    }

    public Double getStartX() {
        return startX;
    }

    public Double getEndX() {
        return endX;
    }

    public String getStatus() {
        return status;
    }
}
