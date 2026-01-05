package jspectrumanalyzer.enciderinterface;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class RxFileReaderOld {

    private String azimut="";

    public @NonNull Flowable<Integer> getObservableAzimuth(String filePath) {
        return Flowable.interval(1, TimeUnit.SECONDS) // Запускаем каждую секунду
                .onBackpressureBuffer() // Используем стратегию BUFFER
                .flatMap(tick -> {
                    try {
                        byte[] arrayBytes = Files.readAllBytes(Paths.get(filePath));
                        // Преобразуем byte[] в int
                        if (arrayBytes.length > 0) {
                            // Например, если предположить, что в файле хранится int в формате little-endian
                            String strValue = new String(arrayBytes);
                            int value = Integer.parseInt(strValue.trim());
                            azimut = String.valueOf(value);
                            return Flowable.just(value);
                        } else {
                            return Flowable.error(new Exception("File does not contain enough data"));
                        }
                    } catch (Exception e) {
                        return Flowable.error(e);
                    }
                })
                .subscribeOn(Schedulers.io());
    }

    public String getAzimut()
    {
        return this.azimut;
    }

}

