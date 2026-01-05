package jspectrumanalyzer.webSocket;

import com.google.protobuf.ByteString;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.socket.client.Socket;
import jspectrumanalyzer.core.*;
import jspectrumanalyzer.socket.JsonExtensions;
import org.json.JSONArray;
import protoModels.*;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static jspectrumanalyzer.Version.getHeaderVersion;
import static jspectrumanalyzer.Version.version;
import static jspectrumanalyzer.core.CompressedUtils.*;
import static jspectrumanalyzer.socket.SocketIOGadalka.QUERY_VERSION_CLIENT;

public class WebSocketManager {

    private final WebSocketArraySpectrum webSocketArraySpectrum;

    private WebSocket webSocket;

    private SpectrumConfig spectrumConfig;

    private final SettingsWSListener settingsWSListener;
    private HttpClient client;
    private MainAddress mainAddress; // Ваш класс с информацией о сервере
    private PublishSubject<Boolean> stopSubject;
    private String isConnectedWS = "отключен";
    private final PropertyChangeSupport supportWS = new PropertyChangeSupport(this);
    private final BehaviorSubject<Boolean> isNotHideWaterfallSubject = BehaviorSubject.createDefault(true);



    public WebSocketManager(WebSocketArraySpectrum webSocketArraySpectrum, SettingsWSListener settingsWSListener) {
        this.webSocketArraySpectrum = webSocketArraySpectrum;
        this.settingsWSListener = settingsWSListener;

    }

    public void setSpectrumConfig(SpectrumConfig config){
        this.spectrumConfig = config;
    }

    // Observable для подключения с повторными попытками
    public Observable<WebSocket> connectWebSocket(String uri) {

        stopSubject = PublishSubject.create();

        return Observable.<WebSocket>create(emitter -> {
            // Создаём слушатель для WebSocket
            WebSocket.Listener listener = new WebSocket.Listener() {

                @Override
                public void onOpen(WebSocket ws) {
                    System.out.println("WebSocket открыт");
                    webSocket = ws;

                    emitter.onNext(ws);
                    ws.request(1);

                    if (spectrumConfig != null) {
                        // Пример отправки первого сообщения после открытия соединения:
                        /*SpectrumWebSocketDataProto spectrumData = createSpectrumData(); // Соберите сообщение согласно вашим настройкам
                        ByteBuffer buffer = ByteBuffer.wrap(spectrumData.toByteArray());
                        ws.sendBinary(buffer, true);*/
                    }
                    // Запрос следующего сообщения
                    //ws.request(1);
                }

                @Override
                public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                    // Обработка входящих бинарных сообщений
                    try {
                        byte[] receivedBytes = new byte[data.remaining()];
                        data.get(receivedBytes);
                        AnalizatorApiProto model = AnalizatorApiProto.parseFrom(receivedBytes);

                        return processReceivedDataAsync(model, ws)
                                .thenRun( () -> {
                                    ws.request(1);
                                })
                                .exceptionally(ex -> {
                                    System.out.println("Ошибка при отправке сообщения: " + ex.getMessage());
                                    ws.request(1);
                                    return null;
                                });
                    } catch (Exception ex) {
                        System.out.println("Ошибка обработки бинарного сообщения: " + ex.getMessage());
                        ws.request(1);
                        return CompletableFuture.completedFuture(null);
                    }
                }

                @Override
                public void onError(WebSocket ws, Throwable error) {
                    System.out.println("Ошибка WebSocket: " + error.getMessage());
                    // В случае ошибки уведомляем observer
                    emitter.onError(error);
                }

                @Override
                public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                    System.out.println("WebSocket закрыт: " + reason);
                    // Если закрытие произошло не по команде stopSendUDP, сигнализируем об ошибке,
                    // чтобы сработал retry
                    if (!stopSubject.hasComplete()) {
                        emitter.onError(new Exception("WebSocket закрыт сервером: " + reason));
                    }
                    return CompletableFuture.completedFuture(null);
                }
            };

            client = HttpClient.newHttpClient();
                    // Попытка установить соединение
            client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(60))
                    .header(QUERY_VERSION_CLIENT, getHeaderVersion())
                    .buildAsync(URI.create(uri), listener)
                    .whenComplete((ws, ex) -> {
                        if (ex != null) {
                            emitter.onError(ex);
                        }
                    });
            })
            // Повторять попытку подключения через 3 секунды, если произошла ошибка, до получения сигнала stop
            .retryWhen(errors ->
                        errors.delay(3, TimeUnit.SECONDS)
                                .takeUntil(stopSubject)
            )
            // Завершить поток, если поступит сигнал stop
            .takeUntil(stopSubject);
    }

    // Пример метода обработки входящих данных
    private CompletableFuture<Boolean> processReceivedDataAsync(AnalizatorApiProto model, WebSocket ws) {
        // Если получено сообщение InfoGadalka
        if (model.hasFullInfo()) {
            try {
                UUID uuidTask = UUID.fromString(model.getFullInfo().getUuidTask());
                ArrayList<MinimalSerifLineProto> listMinimalSerif = new ArrayList<>();
                ListMinimalSerifLineProto listMinimalSerifLineProto = ListMinimalSerifLineProto
                        .newBuilder()
                        .addAllMinimalSerifList(listMinimalSerif)
                        .build();
                byte[] compressedBytesListMinimalSerifLines = compressionBytes(listMinimalSerifLineProto.toByteArray(), 6);
                if (compressedBytesListMinimalSerifLines != null) {

                    ArrayList<ActionCreateSubDiapasonProto> actionCreateSubDiapasonProtoArrayList = new ArrayList<>();
                    ListSelectionSubDiapasonProto listSelectionSubDiapasonProto = ListSelectionSubDiapasonProto
                            .newBuilder()
                            .addAllListSelectionDiapason(actionCreateSubDiapasonProtoArrayList)
                            .build();

                    byte[] compressedBytesActionCreateSubDiapason = compressionBytes(listSelectionSubDiapasonProto.toByteArray(), 6);
                    if (compressedBytesActionCreateSubDiapason != null) {

                        FrequencyRangeProto frequencyRangeProto = FrequencyRangeProto
                                .newBuilder()
                                .setMinFrequency(spectrumConfig.getStartFreq())
                                .setMaxFrequency(spectrumConfig.getEndFreq())
                                .build();

                        InfoGadalkaProto infoGadalkaProto = InfoGadalkaProto
                                .newBuilder()
                                .setFrequencySpectrum(frequencyRangeProto)
                                .setAntennaLNA(spectrumConfig.isAntennaLNA())
                                .setFfTBinHz(spectrumConfig.getFftBins())
                                .setGainLNA(spectrumConfig.getGainLNA())
                                .setSamples(spectrumConfig.getSamples())
                                .setGainVGA(spectrumConfig.getGainVGA())
                                .setSpectrumPaletteStart(spectrumConfig.getStartPalette())
                                .setSpectrumPaletteSize(spectrumConfig.getSizePalette())
                                .setCounterSignal(spectrumConfig.getCounterSignal())
                                .setCounterLose(spectrumConfig.getLoseCounter())
                                .setSubRangeRemote(spectrumConfig.getSubrange())
                                // .setPolarization(spectrumConfig.getAntennaPolarization())
                                .build();

                        FullInfoApiMessageProto fullInfoApiMessageProto = FullInfoApiMessageProto
                                .newBuilder()
                                .setInfoGadalka(infoGadalkaProto)
                                .setSerifTypeAlgorithm(SerifTypeProto.MANUAL)
                                .setMinimalSerifLine(ByteString.copyFrom(compressedBytesListMinimalSerifLines))
                                .setListSelectionSubDiapason(ByteString.copyFrom(compressedBytesActionCreateSubDiapason))
                                .build();

                        WebSocketTaskProto message = WebSocketTaskProto
                                .newBuilder()
                                .setUuidTask(uuidTask.toString())
                                .setMessage(
                                        WebSocketMessageTaskProto
                                                .newBuilder()
                                                .setFullInfo(fullInfoApiMessageProto)
                                                .build()
                                )
                                .build();
                        AnalizatorApiProto analizatorApiProto = AnalizatorApiProto
                                .newBuilder()
                                .setTask(message)
                                .build();

                        ByteBuffer byteBuffer = ByteBuffer.wrap(analizatorApiProto.toByteArray());
                        // Метод sendBinary возвращает CompletableFuture<WebSocket>
                        return ws.sendBinary(byteBuffer, true)
                                .thenApply(sentWs -> {
                                    // Если отправка прошла успешно, возвращаем true
                                    System.out.println("Сообщение успешно отправлено по WebSocket");
                                    return true;
                                })
                                .exceptionally(ex -> {
                                    System.out.println("Ошибка при отправке сообщения: " + ex.getMessage());
                                    return false;
                                });
                    }
                }
            }
            catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
        else if (model.hasTask()) {
            try {
                UUID uuidTask = UUID.fromString(model.getTask().getUuidTask());
                WebSocketMessageTaskProto message = model.getTask().getMessage();
                if (message.hasInfoGadalka()) {
                    InfoGadalkaProto infoGadalkaProto = message.getInfoGadalka();
                    Boolean isApplySetting = settingsWSListener.setSettings(
                            infoGadalkaProto.getFrequencySpectrum().getMinFrequency(),
                            infoGadalkaProto.getFrequencySpectrum().getMaxFrequency(),
                            infoGadalkaProto.getGainLNA(),
                            infoGadalkaProto.getGainVGA(),
                            infoGadalkaProto.getSamples(),
                            infoGadalkaProto.getFfTBinHz(),
                            infoGadalkaProto.getSpectrumPaletteStart(),
                            infoGadalkaProto.getSpectrumPaletteSize(),
                            infoGadalkaProto.getAntennaLNA(),
                            infoGadalkaProto.getCounterSignal(),
                            infoGadalkaProto.getCounterLose(),
                            infoGadalkaProto.getSubRangeRemote(),
                            false
                    );
                    WebSocketStatusMessageProto webSocketStatusMessageProto;
                    if (isApplySetting)
                        webSocketStatusMessageProto = WebSocketStatusMessageProto.ACCEPTED;
                    else webSocketStatusMessageProto = WebSocketStatusMessageProto.NOT_ACCEPTED;

                    AnalizatorApiProto analizatorApiProto = AnalizatorApiProto
                            .newBuilder()
                            .setResponse(
                                    WebSocketResponseProto
                                    .newBuilder()
                                    .setUuidTask(uuidTask.toString())
                                    .setStatus(webSocketStatusMessageProto)
                                    .build()
                            )
                            .build();
                    ByteBuffer byteBuffer = ByteBuffer.wrap(analizatorApiProto.toByteArray());
                    // Метод sendBinary возвращает CompletableFuture<WebSocket>
                    return ws.sendBinary(byteBuffer, true)
                            .thenApply(sentWs -> {
                                // Если отправка прошла успешно, возвращаем true
                                System.out.println("Сообщение sending by request успешно отправлено по WebSocket");
                                return true;
                            })
                            .exceptionally(ex -> {
                                System.out.println("Ошибка при отправке сообщения: " + ex.getMessage());
                                return false;
                            });


                } else if (message.hasSerifTypeAlgorithm()) {
                    SerifTypeProto serifTypeProto = message.getSerifTypeAlgorithm();
                    String variant = switch (serifTypeProto) {
                        case MANUAL -> "Ручная";
                        case AUTO_3 -> "Середина";
                        case AUTO_4 -> "Максимум";
                        case AUTO_5 -> "Мульти максимум";
                        default -> "";
                    };
                    boolean status = settingsWSListener.setTypeSerif(variant);
                    WebSocketStatusMessageProto webSocketStatusMessageProto;
                    if (status)
                        webSocketStatusMessageProto = WebSocketStatusMessageProto.ACCEPTED;
                    else
                        webSocketStatusMessageProto = WebSocketStatusMessageProto.NOT_ACCEPTED;
                    AnalizatorApiProto analizatorApiProto = AnalizatorApiProto
                            .newBuilder()
                            .setResponse(WebSocketResponseProto
                                    .newBuilder()
                                    .setUuidTask(uuidTask.toString())
                                    .setStatus(webSocketStatusMessageProto)
                                    .build()
                            )
                            .build();
                    ByteBuffer byteBuffer = ByteBuffer.wrap(analizatorApiProto.toByteArray());
                    // Метод sendBinary возвращает CompletableFuture<WebSocket>
                    return ws.sendBinary(byteBuffer, true)
                            .thenApply(sentWs -> {
                                // Если отправка прошла успешно, возвращаем true
                                System.out.println("Сообщение serif algorithm успешно отправлено по WebSocket");
                                return true;
                            })
                            .exceptionally(ex -> {
                                System.out.println("Ошибка при отправке сообщения: " + ex.getMessage());
                                return false;
                            });

                } else if (message.hasSubAction()) {
                    SubDiapasonActionProto subDiapasonActionProto = message.getSubAction();
                    if (subDiapasonActionProto.hasCreate()) {
                        TypeSubDiapasonProto typeSubDiapasonProto = subDiapasonActionProto.getCreate().getType();
                        String type = switch (typeSubDiapasonProto) {
                            case FIND -> "scan";
                            case CUT -> "ignore";
                            case UNRECOGNIZED -> "";
                        };
                        boolean status;
                        if (!type.isEmpty())
                            status = settingsWSListener.addSubCreate(type, subDiapasonActionProto.getCreate().getStartFrequency(), subDiapasonActionProto.getCreate().getStartFrequency());
                        else status = false;
                        WebSocketStatusMessageProto webSocketStatusMessageProto;
                        if (status)
                            webSocketStatusMessageProto = WebSocketStatusMessageProto.ACCEPTED;
                        else
                            webSocketStatusMessageProto = WebSocketStatusMessageProto.NOT_ACCEPTED;
                        AnalizatorApiProto analizatorApiProto = AnalizatorApiProto
                                .newBuilder()
                                .setResponse(WebSocketResponseProto
                                        .newBuilder()
                                        .setUuidTask(uuidTask.toString())
                                        .setStatus(webSocketStatusMessageProto)
                                        .build()
                                )
                                .build();
                        ByteBuffer byteBuffer = ByteBuffer.wrap(analizatorApiProto.toByteArray());
                        // Метод sendBinary возвращает CompletableFuture<WebSocket>
                        return ws.sendBinary(byteBuffer, true)
                                .thenApply(sentWs -> {
                                    // Если отправка прошла успешно, возвращаем true
                                    System.out.println("Сообщение subActionCreate успешно отправлено по WebSocket");
                                    return true;
                                })
                                .exceptionally(ex -> {
                                    System.out.println("Ошибка при отправке сообщения: " + ex.getMessage());
                                    return false;
                                });

                    } else if (subDiapasonActionProto.hasDelete()) {

                        float frequency = subDiapasonActionProto.getDelete().getFrequency();
                        boolean status = settingsWSListener.deleteSub(frequency);
                        WebSocketStatusMessageProto webSocketStatusMessageProto;
                        if (status)
                            webSocketStatusMessageProto = WebSocketStatusMessageProto.ACCEPTED;
                        else
                            webSocketStatusMessageProto = WebSocketStatusMessageProto.NOT_ACCEPTED;
                        AnalizatorApiProto analizatorApiProto = AnalizatorApiProto
                                .newBuilder()
                                .setResponse(WebSocketResponseProto
                                        .newBuilder()
                                        .setUuidTask(uuidTask.toString())
                                        .setStatus(webSocketStatusMessageProto)
                                        .build()
                                )
                                .build();
                        ByteBuffer byteBuffer = ByteBuffer.wrap(analizatorApiProto.toByteArray());
                        // Метод sendBinary возвращает CompletableFuture<WebSocket>
                        return ws.sendBinary(byteBuffer, true)
                                .thenApply(sentWs -> {
                                    // Если отправка прошла успешно, возвращаем true
                                    System.out.println("Сообщение subActionDelete успешно отправлено по WebSocket");
                                    return true;
                                })
                                .exceptionally(ex -> {
                                    System.out.println("Ошибка при отправке сообщения: " + ex.getMessage());
                                    return false;
                                });

                    } else if (subDiapasonActionProto.hasDeleteAll()) {
                        boolean status = settingsWSListener.deleteAllSub();
                        //переделать моменты с диапазонами, должно удаляться или добавляться
                        // должно возвращать статус выполнено или нете
                        WebSocketStatusMessageProto webSocketStatusMessageProto;
                        if (status)
                            webSocketStatusMessageProto = WebSocketStatusMessageProto.ACCEPTED;
                        else
                            webSocketStatusMessageProto = WebSocketStatusMessageProto.NOT_ACCEPTED;
                        AnalizatorApiProto analizatorApiProto = AnalizatorApiProto
                                .newBuilder()
                                .setResponse(WebSocketResponseProto
                                        .newBuilder()
                                        .setUuidTask(uuidTask.toString())
                                        .setStatus(webSocketStatusMessageProto)
                                        .build()
                                )
                                .build();
                        ByteBuffer byteBuffer = ByteBuffer.wrap(analizatorApiProto.toByteArray());
                        // Метод sendBinary возвращает CompletableFuture<WebSocket>
                        return ws.sendBinary(byteBuffer, true)
                                .thenApply(sentWs -> {
                                    // Если отправка прошла успешно, возвращаем true
                                    System.out.println("Сообщение subActionDeleteAll успешно отправлено по WebSocket");
                                    return true;
                                })
                                .exceptionally(ex -> {
                                    System.out.println("Ошибка при отправке сообщения: " + ex.getMessage());
                                    return false;
                                });
                    }
                } else if (message.hasMinimalSerifLine()) {
                    List<SubRangeMinimalLineSerif> listMinimalLineSerif = new ArrayList<>();
                    byte[] minimalLine = uncompressedBytes(message.getMinimalSerifLine().toByteArray());
                    ListMinimalSerifLineProto listMinimalSerifLineProto = ListMinimalSerifLineProto.parseFrom(minimalLine);
                    for (int i = 0; i < listMinimalSerifLineProto.getMinimalSerifListCount(); i++) {
                        MinimalSerifLineProto minimalSerifLineProto = listMinimalSerifLineProto.getMinimalSerifList(i);
                        float serifLine = minimalSerifLineProto.getSerifLine();
                        float startFreqSerifLine = minimalSerifLineProto.getFrequencyStart();
                        float endFreqSerifLine = minimalSerifLineProto.getFrequencyEnd();
                        SubRangeMinimalLineSerif subRangeMinimalLineSerif = new SubRangeMinimalLineSerif(startFreqSerifLine, endFreqSerifLine, (int) serifLine);
                        listMinimalLineSerif.add(subRangeMinimalLineSerif);
                    }
                    boolean status = settingsWSListener.changeSerifLine(listMinimalLineSerif);
                    WebSocketStatusMessageProto webSocketStatusMessageProto;
                    if (status)
                        webSocketStatusMessageProto = WebSocketStatusMessageProto.ACCEPTED;
                    else
                        webSocketStatusMessageProto = WebSocketStatusMessageProto.NOT_ACCEPTED;
                    AnalizatorApiProto analizatorApiProto = AnalizatorApiProto
                            .newBuilder()
                            .setResponse(WebSocketResponseProto
                                    .newBuilder()
                                    .setUuidTask(uuidTask.toString())
                                    .setStatus(webSocketStatusMessageProto)
                                    .build()
                            )
                            .build();
                    ByteBuffer byteBuffer = ByteBuffer.wrap(analizatorApiProto.toByteArray());
                    // Метод sendBinary возвращает CompletableFuture<WebSocket>
                    return ws.sendBinary(byteBuffer, true)
                            .thenApply(sentWs -> {
                                // Если отправка прошла успешно, возвращаем true
                                System.out.println("Сообщение minimalSerifLine успешно отправлено по WebSocket");
                                return true;
                            })
                            .exceptionally(ex -> {
                                System.out.println("Ошибка при отправке сообщения: " + ex.getMessage());
                                return false;
                            });
                } else if (message.hasFullInfo()) {
                    FullInfoApiMessageProto fullInfoApiMessageProto = message.getFullInfo();
                    InfoGadalkaProto infoGadalkaProto = fullInfoApiMessageProto.getInfoGadalka();
                    List<SubRangeMinimalLineSerif> listMinimalLineSerif = new ArrayList<>();
                    if (fullInfoApiMessageProto.hasMinimalSerifLine()) {
                        byte[] minimalSerifLineCompress = fullInfoApiMessageProto.getMinimalSerifLine().toByteArray();
                        byte[] minimalLine = uncompressedBytes(minimalSerifLineCompress);
                        ListMinimalSerifLineProto listMinimalSerifLineProto = ListMinimalSerifLineProto.parseFrom(minimalLine);

                        for (int i = 0; i < listMinimalSerifLineProto.getMinimalSerifListCount(); i++) {
                            MinimalSerifLineProto minimalSerifLineProto = listMinimalSerifLineProto.getMinimalSerifList(i);
                            float serifLine = minimalSerifLineProto.getSerifLine();
                            float startFreqSerifLine = minimalSerifLineProto.getFrequencyStart();
                            float endFreqSerifLine = minimalSerifLineProto.getFrequencyEnd();
                            SubRangeMinimalLineSerif subRangeMinimalLineSerif = new SubRangeMinimalLineSerif(startFreqSerifLine, endFreqSerifLine, (int) serifLine);
                            listMinimalLineSerif.add(subRangeMinimalLineSerif);
                        }
                    }
                    List<RangeForSerif> rangeForSerifList = new ArrayList<>();
                    if (fullInfoApiMessageProto.hasListSelectionSubDiapason()){
                        byte[] listSubDiapasonCompress = fullInfoApiMessageProto.getListSelectionSubDiapason().toByteArray();
                        byte[] listSubDiapason = uncompressedBytes(listSubDiapasonCompress);
                        ListSelectionSubDiapasonProto listSelectionSubDiapasonProto = ListSelectionSubDiapasonProto.parseFrom(listSubDiapason);
                        List<ActionCreateSubDiapasonProto> listSelectionSubDiapasonProtos = listSelectionSubDiapasonProto.getListSelectionDiapasonList();

                        for (ActionCreateSubDiapasonProto list : listSelectionSubDiapasonProtos) {
                            double startFrequency = list.getStartFrequency();
                            double endFrequency = list.getEndFrequency();
                            String type = switch (list.getType()) {
                                case FIND -> "scan";
                                case CUT -> "ignore";
                                case UNRECOGNIZED -> "";
                            };
                            RangeForSerif rangeForSerif = new RangeForSerif(startFrequency, endFrequency, type);
                            rangeForSerifList.add(rangeForSerif);
                        }
                    }

                    SerifTypeProto serifTypeProto = fullInfoApiMessageProto.getSerifTypeAlgorithm();
                    String variant = switch (serifTypeProto) {
                        case MANUAL -> "Ручная";
                        case AUTO_3 -> "Середина";
                        case AUTO_4 -> "Максимум";
                        case AUTO_5 -> "Мульти максимум";
                        default -> "";
                    };
                    boolean status = settingsWSListener.setAllSettings(infoGadalkaProto.getFrequencySpectrum().getMinFrequency(),
                            infoGadalkaProto.getFrequencySpectrum().getMaxFrequency(),
                            infoGadalkaProto.getGainLNA(),
                            infoGadalkaProto.getGainVGA(),
                            infoGadalkaProto.getSamples(),
                            infoGadalkaProto.getFfTBinHz(),
                            infoGadalkaProto.getSpectrumPaletteStart(),
                            infoGadalkaProto.getSpectrumPaletteSize(),
                            infoGadalkaProto.getAntennaLNA(),
                            infoGadalkaProto.getCounterSignal(),
                            infoGadalkaProto.getCounterLose(),
                            infoGadalkaProto.getSubRangeRemote(),
                            false,
                            variant, rangeForSerifList,
                            listMinimalLineSerif);

                    WebSocketStatusMessageProto webSocketStatusMessageProto;
                    if (status)
                        webSocketStatusMessageProto = WebSocketStatusMessageProto.ACCEPTED;
                    else
                        webSocketStatusMessageProto = WebSocketStatusMessageProto.NOT_ACCEPTED;
                    AnalizatorApiProto analizatorApiProto = AnalizatorApiProto
                            .newBuilder()
                            .setResponse(WebSocketResponseProto
                                    .newBuilder()
                                    .setUuidTask(uuidTask.toString())
                                    .setStatus(webSocketStatusMessageProto)
                                    .build()
                            )
                            .build();
                    ByteBuffer byteBuffer = ByteBuffer.wrap(analizatorApiProto.toByteArray());
                    // Метод sendBinary возвращает CompletableFuture<WebSocket>
                    return ws.sendBinary(byteBuffer, true)
                            .thenApply(sentWs -> {
                                // Если отправка прошла успешно, возвращаем true
                                System.out.println("Сообщение fullInfo успешно отправлено по WebSocket");
                                return true;
                            })
                            .exceptionally(ex -> {
                                System.out.println("Ошибка при отправке сообщения: " + ex.getMessage());
                                return false;
                            });


                } else if (message.hasHideWaterfall()) {
                    isNotHideWaterfallSubject.onNext(message.getHideWaterfall().getVisible());
                    System.out.println(message.getHideWaterfall().getVisible());
                    WebSocketStatusMessageProto webSocketStatusMessageProto;
                    webSocketStatusMessageProto = WebSocketStatusMessageProto.ACCEPTED;
                    AnalizatorApiProto analizatorApiProto = AnalizatorApiProto
                            .newBuilder()
                            .setResponse(WebSocketResponseProto
                                    .newBuilder()
                                    .setUuidTask(uuidTask.toString())
                                    .setStatus(webSocketStatusMessageProto)
                                    .build()
                            )
                            .build();
                    ByteBuffer byteBuffer = ByteBuffer.wrap(analizatorApiProto.toByteArray());
                    // Метод sendBinary возвращает CompletableFuture<WebSocket>
                    return ws.sendBinary(byteBuffer, true)
                            .thenApply(sentWs -> {
                                // Если отправка прошла успешно, возвращаем true
                                System.out.println("Сообщение HideWaterfall успешно отправлено по WebSocket");
                                return true;
                            })
                            .exceptionally(ex -> {
                                System.out.println("Ошибка при отправке сообщения: " + ex.getMessage());
                                return false;
                            });
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
        return CompletableFuture.completedFuture(true);
    }


    private Disposable listenerWebSocket;
    private Disposable sendingWebSocket;
    private Disposable hideWaterfallObservable;

    public void registerSocketListeners(Socket socket, String hackrfID) {
        socket.on("startSendUDP", objects -> {
            // Пример разбора данных и формирования адреса подключения:
            try {
                JSONArray array = (JSONArray) objects[0];
                byte[] arrayLast = JsonExtensions.toByteArray(array);
                if (arrayLast == null) return;
                InfoChartConnectProto model = InfoChartConnectProto.parseFrom(arrayLast);
                System.out.println("Получено событие startSendUDP: " + model.toString());

                mainAddress = new MainAddress(model.getHostName(), model.getPortWs(), model.getServerGadalkaID());
                String uri = "ws://" + mainAddress.address + ":" + mainAddress.port + "/charts?userID=" + hackrfID + "&gadalkaServerID=" + mainAddress.serverGadalkaId;

                // Запускаем поток подключения с переподключениями

                if (listenerWebSocket != null && !listenerWebSocket.isDisposed()){
                    listenerWebSocket.dispose();
                }
                if (sendingWebSocket != null && !sendingWebSocket.isDisposed()){
                    sendingWebSocket.dispose();
                }
                if (hideWaterfallObservable != null && !hideWaterfallObservable.isDisposed()){
                    hideWaterfallObservable.dispose();
                }
                isNotHideWaterfallSubject.onNext(true);

                listenerWebSocket = connectWebSocket(uri)
                        .subscribe(ws -> {
                            System.out.println("Подключение установлено.");
                            String oldIsConnected = isConnectedWS;
                            isConnectedWS = "подключен";
                            supportWS.firePropertyChange("statusWS",oldIsConnected,isConnectedWS);
                            hideWaterfallObservable = isNotHideWaterfallSubject
                                    .subscribeOn(Schedulers.io())
                                    .subscribe(isVisible -> {
                                if (sendingWebSocket != null && !sendingWebSocket.isDisposed()){
                                    sendingWebSocket.dispose();
                                }
                                if (isVisible){
                                    sendingWebSocket = startSendingData(ws);
                                }
                            });

                        }, error -> {
                            System.out.println("Ошибка подключения: " + error.getMessage());
                        });


            } catch (Exception ex) {
                System.out.println("Ошибка обработки startSendUDP: " + ex.getMessage());
            }
        });

        socket.on("stopSendUDP", objects -> {

            if (webSocket != null) {

                if (listenerWebSocket != null && !listenerWebSocket.isDisposed()) {
                    listenerWebSocket.dispose();
                }
                if (sendingWebSocket != null && !sendingWebSocket.isDisposed()) {
                    sendingWebSocket.dispose();
                }
                if (hideWaterfallObservable != null && !hideWaterfallObservable.isDisposed()){
                    hideWaterfallObservable.dispose();
                }
                isNotHideWaterfallSubject.onNext(true);

                stop()
                    .thenRun(() -> {
                        client.close();
                        webSocket = null;
                        stopSubject = null;
                        System.out.println("Websocket current close");
                        String oldIsConnected = isConnectedWS;
                        isConnectedWS = "отключен";
                        supportWS.firePropertyChange("statusWS",oldIsConnected,isConnectedWS);
                    })
                    .exceptionally(ex -> {
                        client.close();
                        webSocket = null;
                        stopSubject = null;
                        System.out.println("Error closing webSocketServer: " + ex.getMessage());
                        return null;
                    });
            }
        });
    }

    // Метод для периодической отправки данных по WebSocket
    public Disposable startSendingData(WebSocket ws) {
        // Создаем Observable, который генерирует событие каждые 1 секунду.
        // Можно изменить интервал по необходимости.
        return Observable.interval(40, TimeUnit.MILLISECONDS)
                // Завершаем отправку, если поступит сигнал из stopSubject (например, событие stopSendUDP)
                .takeUntil(stopSubject)
                .subscribeOn(Schedulers.io())
                .subscribe(tick -> {
                    int azimuth = webSocketArraySpectrum.getAzimuth();
                    byte[] compressed = compressFloatArray(webSocketArraySpectrum.getNumber());
                    AnalizatorApiProto analizatorApiProto = AnalizatorApiProto
                            .newBuilder()
                            .setSpectrumData(
                                    WebSocketSpectrumProto
                                            .newBuilder()
                                            .setData(ByteString.copyFrom(compressed))
                                            .setAzimuth(azimuth)
                                            .build()
                            )
                            .build();
                    /*SpectrumZSTDProto spectrumZSTDProto = SpectrumZSTDProto
                            .newBuilder()
                            .setData(ByteString.copyFrom(compressed))
                            .setAzimuth(webSocketArraySpectrum.getAzimuth())
                            .build();
                    SpectrumWebSocketDataProto spectrumWebSocketDataProto = SpectrumWebSocketDataProto
                            .newBuilder()
                            .setSpectrumData(spectrumZSTDProto)
                            .build();*/
                    ByteBuffer byteBuffer = ByteBuffer.wrap(analizatorApiProto.toByteArray());

                    ws.sendBinary(byteBuffer, true).thenRun(() -> {
                        //System.out.println("Бинарное сообщение отправлено: ");
                    }).exceptionally(e -> {
                        System.err.println("Ошибка при отправке бинарного сообщения: " + e.getMessage());
                        if (ws.isOutputClosed()) {
                            System.out.println("websocket закрыт");
                        }
                        return null;
                    });
                }, error -> {
                    System.out.println("Ошибка в отправке данных: " + error.getMessage());
                });
    }

    public CompletableFuture<Void> stop() {
        System.out.println("Инициирована принудительная остановка. Отправляем сообщение закрытия.");
        if (webSocket != null) {
            return webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Закрыт по причине сервера")
                    .thenRun(() -> {
                        // После успешной отправки закрывающего сообщения сигнализируем остановку
                        stopSubject.onNext(true);
                        stopSubject.onComplete();
                        System.out.println("Закрытие завершено.");
                    })
                    .exceptionally(ex -> {
                        System.out.println("Ошибка при отправке закрывающего сообщения: " + ex.getMessage());
                        // Даже в случае ошибки сигнализируем остановку
                        stopSubject.onNext(true);
                        stopSubject.onComplete();
                        return null;
                    });
        } else {
            stopSubject.onNext(true);
            stopSubject.onComplete();
            return CompletableFuture.completedFuture(null);
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        supportWS.addPropertyChangeListener(listener);
    }

}
