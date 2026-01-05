package jspectrumanalyzer.enciderinterface;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import jspectrumanalyzer.core.AzimuthDbm;
import jspectrumanalyzer.core.NumberSource;
import jspectrumanalyzer.socket.SocketIOGadalka;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ConditionalDataListener2 {


    private final BehaviorSubject<AzimuthDbm> dataSubject = BehaviorSubject.create();
    private final NumberSource numberSource;
    private boolean check = false;
    private Integer startAzimuth;
    private Integer endAzimuth;

    public ConditionalDataListener2(NumberSource numberSource) {
        this.numberSource = numberSource;
        // Создаем Observable, который каждую секунду проверяет условие
        Observable.interval(300, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io()) // Выполнение в
                /// отдельном потоке
                .subscribe(time -> {
                     if (Objects.equals(numberSource.getCurrentSerifVariant(), "Середина")
                            || Objects.equals(numberSource.getCurrentSerifVariant(), "Максимум")) {
                        if (numberSource.getNumber() > numberSource.getCounterForSerif() && !check) {
                            check = true;
                        }
                        if (numberSource.getNumber() < numberSource.getCounterForSerif() && check) {
                            check = false;
                            AzimuthDbm maxRecord = numberSource.records.getFirst(); // Инициализируем первым элементом
                            for (AzimuthDbm record : numberSource.records) {
                                if (record.getDbm() > maxRecord.getDbm()) {
                                    maxRecord = record; // Обновляем запись с максимальным dBm
                                }
                            }
                            Integer azimuth = maxRecord.getAzimuth();
                            numberSource.records.clear();
                            dataSubject.onNext(maxRecord);
                        }
                        if (numberSource.getNumber() == 0 && !numberSource.records.isEmpty()){
                            numberSource.records.clear();
                        }
                    }
                }, Throwable::printStackTrace);
    }

    public Observable<AzimuthDbm> getDataObservable() {
        return dataSubject;
    }
}
