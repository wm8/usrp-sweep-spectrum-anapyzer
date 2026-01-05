package jspectrumanalyzer.core;

import java.util.List;

public interface SettingsWSListener {
    Boolean setSettings(int startFreq,
                        int endFreq,
                        int gainLNA,
                        int gainVGA,
                        int samples,
                        int fftBins,
                        int startPalette,
                        int sizePalette,
                        boolean antennaLNA,
                        int counterSignal,
                        int loseCounter,
                        int subRange,
                        boolean antennaPolarization);

    boolean setTypeSerif(String serifVariant);
    // переделать под каждый параметр отдельно
    boolean addSubCreate(String type,double startFreq, double endFreq);

    boolean deleteSub(double freq);

    boolean deleteAllSub();
    void changeAntennaPolarization(boolean antennaPolarization);

    boolean changeSerifLine(List<SubRangeMinimalLineSerif> subRangeMinimalLineSerifList);

    boolean setAllSettings(
            int startFreq,
            int endFreq,
            int gainLNA,
            int gainVGA,
            int samples,
            int fftBins,
            int startPalette,
            int sizePalette,
            boolean antennaLNA,
            int counterSignal,
            int loseCounter,
            int subRange,
            boolean antennaPolarization,
            String serifVariant,
            List<RangeForSerif> rangeForSerifList,
            List<SubRangeMinimalLineSerif> subRangeMinimalLineSerifList
    );
}
