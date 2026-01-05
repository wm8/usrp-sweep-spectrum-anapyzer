package jspectrumanalyzer.core;

public interface SerifListenner {

    public void serifMessage(Integer startMHz, Integer endMHz, String hackrfid);

    public void serifMessage2(Integer startMHz, Integer endMHz, String hackrfid, Integer azimuth, float dbm);
}
