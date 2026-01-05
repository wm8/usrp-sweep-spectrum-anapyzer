package jspectrumanalyzer.enciderinterface;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RxFileReader {
    private final String filePath;
    private String lastReadContent = "";
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public RxFileReader(String filePath) {
        this.filePath = filePath;
    }

    // Метод для запуска периодического чтения файла
    public synchronized void startReading() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
                String content = new String(fileBytes);
                lastReadContent = content;
            } catch (IOException e) {
                // Обработка ошибки чтения файла
                System.err.println("Ошибка чтения файла: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    // Метод для получения последнего прочитанного содержимого файла
    public String getLastReadContent() {
        return lastReadContent;
    }

    // Завершение работы сервиса
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

