package jspectrumanalyzer.core;

import java.util.ArrayList;
import java.util.List;

public class NumberSource {

    private int currentNumber;
    private int currentAzimuth;
    private String currentSerifVariant;
    public List<AzimuthDbm> records = new ArrayList<>();
    private Integer counterForSerif = 5;
    private List<Integer> listCounterNumber = new ArrayList<>();
    private List<SubRangeResult> subRangeResultList = new ArrayList<>();
    // для авто5



    // Метод для изменения числа
    public void setNumber(int number) {
        this.currentNumber = number;
    }

    // Метод для получения текущего числа
    public int getNumber() {
        return currentNumber;
    }

    public void setCurrentAzimuth(int azimuth) {
        this.currentAzimuth = azimuth;
    }

    public int getCurrentAzimuth() {
        return currentAzimuth;
    }

    public String getCurrentSerifVariant(){
        return currentSerifVariant;
    }
    public void setCurrentSerifVariant(String serifVariant){
        this.currentSerifVariant = serifVariant;
    }

    public Integer getCounterForSerif(){
        return counterForSerif;
    }

    public void setCounterForSerif(Integer counter){
        this.counterForSerif = counter;
    }

    public void setListCounterNumber(List<Integer> list) {
        this.listCounterNumber = list;
    }

    public List<Integer> getListCounterNumber() {
        return listCounterNumber;
    }

    public void setSubRangeResultList(List<SubRangeResult> subRangeResultList) {
        this.subRangeResultList = subRangeResultList;
    }

    public List<SubRangeResult> getSubRangeResultList(){
        return subRangeResultList;
    }

}