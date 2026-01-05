package jspectrumanalyzer.enciderinterface;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import jspectrumanalyzer.core.NumberSource;

import java.util.concurrent.TimeUnit;

public class ConditionalDataListener {

    private final BehaviorSubject<Integer> dataSubject = BehaviorSubject.create();
    private final NumberSource numberSource;

    public ConditionalDataListener(NumberSource numberSource) {
        this.numberSource = numberSource;
        // Создаем Observable, который каждую секунду проверяет условие
        Observable.interval(1, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())  // Выполнение в отдельном потоке
                .subscribe(time -> {
                    // Проверяем условие (например, текущее время должно быть четным числом секунд)
                    if (numberSource.getNumber() > 5) {
                        // Эмитируем значение, если условие выполнено
                        dataSubject.onNext(numberSource.getNumber());

                    }
                }, Throwable::printStackTrace);
    }

    public Observable<Integer> getDataObservable() {
        return dataSubject;
    }
}
