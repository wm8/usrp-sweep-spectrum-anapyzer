package jspectrumanalyzer;

import io.reactivex.rxjava3.functions.Consumer;
import jspectrumanalyzer.enciderinterface.RxFileReader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileListener {
    private final RxFileReader fileReaderService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    public int diap = 1;

    public FileListener(RxFileReader fileReaderService) {
        this.fileReaderService = fileReaderService;
    }

    public void startListening(Consumer<String> dataHandler, HackRFSweepSpectrumAnalyzer hackRFSweepSpectrumAnalyzer) {
        executorService.submit(() -> listenToFileChanges(dataHandler, hackRFSweepSpectrumAnalyzer));
    }

    private synchronized void listenToFileChanges(Consumer<String> dataHandler, HackRFSweepSpectrumAnalyzer hackRFSweepSpectrumAnalyzer) {
        while (!Thread.currentThread().isInterrupted()) {
            String content = fileReaderService.getLastReadContent();
            if (!content.isEmpty()) {
                try {
                    if (hackRFSweepSpectrumAnalyzer == null)
                        diap = 1;
                    else
                        diap = hackRFSweepSpectrumAnalyzer.getDiap()/100; // Получаем данные из WaterfallPlot
                    //System.out.println("diap wat= " + diap);


                    dataHandler.accept(content);
                } catch (Throwable e) {
                    System.out.println(e.getMessage());
                }

                try {
                    Thread.sleep(1000 * diap);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        }
    }

    public void shutdown() {
        executorService.shutdownNow();
    }

    public void pause(int i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}