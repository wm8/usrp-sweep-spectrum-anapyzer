package jspectrumanalyzer.enciderinterface;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import jspectrumanalyzer.socket.SocketIOGadalka;

public class SocketDataListener {

    // Используем BehaviorSubject для эмиссии значений типа Integer
    private final BehaviorSubject<Integer> azimuthSubject = BehaviorSubject.create();

    // Конструктор принимает объект сокета, который предоставляет данные
    public SocketDataListener(SocketIOGadalka socketIOGadalka) {
        // Создаем Observable для получения данных от сокета и запускаем его на отдельном потоке
        Observable.<Integer>create(emitter -> {
                    while (true) {
                        try {
                            // Получаем значение из метода сокета
                            int azimuth = socketIOGadalka.getAzimuth();

                            //System.out.println("azzz "+azimuth);
                            // Эмитируем значение
                            emitter.onNext(azimuth);

                            // Устанавливаем небольшую задержку для эмуляции периодичности получения данных
                            Thread.sleep(100);  // Пример, можно изменить
                        } catch (Exception e) {
                            emitter.onError(e);
                        }
                    }
                })
                // Переводим выполнение Observable в отдельный поток
                .subscribeOn(Schedulers.io())
                // Подписываемся и передаем данные в BehaviorSubject
                .subscribe(azimuthSubject::onNext, Throwable::printStackTrace);
    }

    // Метод, который возвращает Observable для подписки на данные
    public Observable<Integer> getAzimuthObservable() {
        return azimuthSubject;
    }
}