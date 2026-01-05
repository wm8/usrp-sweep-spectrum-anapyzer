package jspectrumanalyzer.core;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static jspectrumanalyzer.HackRFSweepSpectrumAnalyzer.hackrfID;

public class SignalStreamProcessor {

    private final Observable<List<SubRangeResult>> signalStream;
    private final ConfigSerif configSerif;
    private int lastSubRangeStep = -1;

    private final Map<Long, SubRangeResult> subRangeResults = new HashMap<>(); // Хранит прошлые результаты

    // Буфер для хранения данных (dbm и азимуты)
    private final Queue<SignalData> bufferQueue = new LinkedList<>();

    // Конструктор для создания потока
    public SignalStreamProcessor( ConfigSerif configSerif) {
        this.configSerif = configSerif;
        this.signalStream = Observable.create((ObservableOnSubscribe<List<SubRangeResult>>) emitter -> {
                    // Подписка на данные и их обработка
                    while (!emitter.isDisposed()) {
                        synchronized (bufferQueue) {
                            if (!bufferQueue.isEmpty()) {
                                SignalData signalData = bufferQueue.poll();
                                //System.out.println(configSerif.getSubRangeStep());
                                List<SubRangeResult> results = processSignal(
                                        configSerif.getCurrentStartFrequency(),
                                        configSerif.getCurrentEndFrequency(),
                                        configSerif.getFrequencyStep(),
                                        signalData.dbmValues,
                                        signalData.azimuthValue,
                                        configSerif.getMinDbLine(),
                                        configSerif.getAboveThresholdLimit(),
                                        configSerif.getBelowThresholdLimit(),
                                        configSerif.getRangeForSerifs(),
                                        configSerif.getSubRangeStep()
                                );
                                if (!results.isEmpty()){
                                    for (SubRangeResult result:results){
                                        subRangeResults.remove(result.startFreq,result);
                                    }
                                    emitter.onNext(results);
                                    }
                            }
                        }
                    }
                })
                .observeOn(Schedulers.io())
                .share(); // Разделяем поток на несколько подписчиков
    }

    // Метод для обработки данных из буфера
    public void addDataToBuffer(SignalData signalData) {
        synchronized (bufferQueue) {
            bufferQueue.offer(signalData);
        }
    }

    // Метод для обработки сигнала
    private  List<SubRangeResult> processSignal(
            int startFreq, int endFreq, int step, float[] dbmValues, int azimuthValue,
            List<SubRangeMinimalLineSerif> perSubRangeThresholds, int aboveThresholdLimit, int belowThresholdLimit, List<RangeForSerif> rangeForSerifs, int subRange) {

        // Переводим частоты в Гц, используем long
        long startFreqHz = startFreq * 1_000_000L;
        long endFreqHz = endFreq * 1_000_000L;
        long subRangeHz = subRange * 1_000_000L;
        if (subRange != lastSubRangeStep) {
            subRangeResults.clear(); // Очищаем старые данные
            lastSubRangeStep = subRange;
        }

        long subRangeSize = subRangeHz / step;
        List<long[]> subRanges = new ArrayList<>();
        //System.out.println("начало "+ System.currentTimeMillis());

        for (long i = startFreqHz; i < endFreqHz; i += subRangeHz) {
            long subRangeEnd = Math.min(i + subRangeHz , endFreqHz);
            subRanges.add(new long[]{i, subRangeEnd});
        }

        List<SubRangeResult> results = new ArrayList<>();

        for (long[] range : subRanges) {
            long subRangeStart = range[0];
            long subRangeEnd = range[1];
            int subRangeStartMHz = (int) (subRangeStart / 1_000_000);
            int subRangeEndMHz = (int) (subRangeEnd / 1_000_000);
            int threshold = getThresholdForSubRange(subRangeStartMHz, subRangeEndMHz, perSubRangeThresholds);
            int startIndex = (int) ((subRangeStart - startFreqHz) / step);
            int endIndex = (int) (Math.min(startIndex + subRangeSize, dbmValues.length));

            float maxDbm = Float.NEGATIVE_INFINITY;
            int maxDbmIndex = startIndex;

            for (int j = startIndex; j < endIndex; j++) {
                if (dbmValues[j] > maxDbm) {
                    maxDbm = dbmValues[j];
                    maxDbmIndex = j;
                }
            }

            SubRangeResult result = subRangeResults.get(subRangeStart);

            if (result == null) {
                result = new SubRangeResult(subRangeStart, subRangeEnd, maxDbm, azimuthValue);
                subRangeResults.put(subRangeStart, result);
            }

            // Проверяем, не превышает ли новое значение предыдущее максимальное
            if (maxDbm > result.maxDbm) {
                result.maxDbm = maxDbm; // Обновляем только если новое значение выше
                result.currentAzimuth = azimuthValue;
            }

            double sbrsgart = (double) subRangeStart / 1000000;
            double sbrend = (double) subRangeEnd / 1000000;

            boolean isIgnored = false;
            for (RangeForSerif rangeForSerif : rangeForSerifs) {
                if (sbrsgart > rangeForSerif.getStartX() && sbrend < rangeForSerif.getEndX() && "ignore".equals(rangeForSerif.getStatus())) {
                    isIgnored = true;
                    break;
                }
            }

            if (isIgnored)
                continue;

            // Логика счетчиков
            if (maxDbm > threshold) {
                result.aboveThresholdCount++;
                result.belowThresholdCount = 0;
            } else if (result.aboveThresholdCount > aboveThresholdLimit) {
                result.belowThresholdCount++;
                try {
                    FileWriter writer = new FileWriter("/home/user/gadalkaLogs/analizatorLogs/signallogs/"+ hackrfID +".txt", true);
                    writer.write(result.belowThresholdCount + ";");
                    //writer.append('\n');
                    writer.close();
                } catch (IOException e) {
                    System.out.println("Ошибка при записи в файл");
                    e.printStackTrace();
                }
                if (result.belowThresholdCount >= belowThresholdLimit) {
                    try {
                        FileWriter writer = new FileWriter("/home/user/gadalkaLogs/analizatorLogs/signallogs/"+ hackrfID +".txt", true);
                        writer.write("Сигнал потерян");
                        writer.append('\n');
                        writer.close();
                    } catch (IOException e) {
                        System.out.println("Ошибка при записи в файл");
                        e.printStackTrace();
                    }
                    System.out.println("Сигнал потерян");
                    result.aboveThresholdCount = 0;
                    result.belowThresholdCount = 0;
                    results.add(result);
                }
            }
            subRangeResults.put(subRangeStart, result);
        }
        //System.out.println("конец "+ System.currentTimeMillis());

        return results;
    }

    private int getThresholdForSubRange(int startMHz, int endMHz, List<SubRangeMinimalLineSerif> list) {
        for (SubRangeMinimalLineSerif s : list) {
            if (s.getStartFreq() == startMHz && s.getEndFreq() == endMHz) {
                return s.getDbLine();
            }
        }
        // Значение по умолчанию, если не найдено
        return Integer.MIN_VALUE;
    }

    // Метод для подписки на поток и обработки
    public Observable<List<SubRangeResult>> getSignalStream() {
        return signalStream;
    }

}
