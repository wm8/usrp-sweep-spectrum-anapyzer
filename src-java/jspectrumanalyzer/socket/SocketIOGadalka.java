package jspectrumanalyzer.socket;


import io.socket.client.IO;
import io.socket.engineio.client.transports.Polling;
import io.socket.engineio.client.transports.WebSocket;
import jspectrumanalyzer.core.SettingsWSListener;
import jspectrumanalyzer.core.WebSocketArraySpectrum;
import jspectrumanalyzer.webSocket.SpectrumConfig;
import jspectrumanalyzer.webSocket.WebSocketManager;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jspectrumanalyzer.Version.getHeaderVersion;
import static jspectrumanalyzer.Version.version;

public class SocketIOGadalka {


    private final io.socket.client.Socket socket;
    private Integer azimuth =0;
    public DatagramSocket ds;
    public static String QUERY_VERSION_CLIENT = "version";


    private String isConnected = "отключен";
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    private String hackrfID;

    private WebSocketManager webSocketManager;

    public SocketIOGadalka(
            String hackrfID,
            URI socketURL,
            String okkoID,
            SettingsWSListener settingsWSListener,
            WebSocketArraySpectrum webSocketArraySpectrum
    ){
        this.hackrfID = hackrfID;

        Map<String, List<String>> extraHeaders = new HashMap<>();

// Например, добавляем заголовок "Authorization" с одним значением
        extraHeaders.put(QUERY_VERSION_CLIENT, Collections.singletonList(getHeaderVersion()));

        IO.Options options = IO.Options.builder()
                .setQuery("typeDevice=okko_device&okkoID=" + okkoID)
                .setExtraHeaders(extraHeaders)
                .setTimeout(90000L)
                .setReconnection(true)
                .setReconnectionDelay(1500L)
                .setReconnectionDelayMax(3500L)
                .setTransports(new String[]{WebSocket.NAME, Polling.NAME})
                .build();

        socket = IO.socket(socketURL, options).connect();

        webSocketManager = new WebSocketManager(
                webSocketArraySpectrum,
                settingsWSListener
        );
        setupEventListeners(socketURL);
    }

    private void setupEventListeners(URI socketURL){
        socket.on(io.socket.client.Socket.EVENT_CONNECT, objects -> {
            String oldIsConnected = isConnected;
            isConnected = "подключен";
            System.out.println("Подключено к сокету по " + socketURL);
            support.firePropertyChange("statusSocketIO",oldIsConnected,isConnected);
            try {
                ds = new DatagramSocket();
            } catch (SocketException e) {
                System.out.println(e.getMessage());
            }
        });
        socket.on(io.socket.client.Socket.EVENT_CONNECT_ERROR, objects -> {
            System.out.println("Ошибка подключения к сокету по " + socketURL);
        });
        socket.on(io.socket.client.Socket.EVENT_DISCONNECT, objects -> {
            System.out.println("Отключен от сокета по " + socketURL);
            String oldIsConnected = isConnected;
            isConnected = "отключен";
            support.firePropertyChange("statusSocketIO",oldIsConnected,isConnected);
            //socket.off();
            //socket.close();
            //вынести ивенты в функцию
            // сделать повторный реконнект
        });
        socket.on("azimuthValue", objects -> {
            for (Object object: objects) {
                if (object instanceof Integer) {
                    azimuth = (Integer) object;
                    System.out.println("Азимут OKO: " + azimuth);
                } else {
                    System.out.println("Неизвестный тип: " + object.getClass().getSimpleName());
                }
            }
            //azimuth = (Integer) objects[0];
            System.out.println("Азимут OKO " + objects[0].toString());
        });

        if (webSocketManager != null) {
            webSocketManager.registerSocketListeners(socket, hackrfID);
        }
    }

    public synchronized Integer getAzimuth(){
        return azimuth;
    }

    public void serifMessage(byte [] serifMessage){
        if (socket.connected()){
            socket.emit("serifMessage", (Object) convertByteArray(serifMessage));
        }
    }

    public void closeConnect(){
        socket.off();
        socket.disconnect();
        socket.close();
        System.out.println("Сокет отключен и закрыт");
    }

    public void setSpectrumConfig(SpectrumConfig config){
        if (webSocketManager != null){
            webSocketManager.setSpectrumConfig(config);
        }
    }


    // Метод для конвертации byte[] в Byte[]
    private Byte[] convertByteArray(byte[] byteArray) {
        // Создаем новый массив Byte длиной как у byteArray
        Byte[] byteObjectArray = new Byte[byteArray.length];

        // Заполняем новый массив, конвертируя каждый элемент
        for (int i = 0; i < byteArray.length; i++) {
            byteObjectArray[i] = byteArray[i]; // Автоматическая упаковка
        }

        return byteObjectArray;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    public WebSocketManager getWebSocketManager() {
        return webSocketManager;
    }

    public String getIsConnected(){
        return isConnected;
    }

}

