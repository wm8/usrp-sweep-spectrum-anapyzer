package jspectrumanalyzer.socket;

import java.io.IOException;
import java.net.*;
import java.util.zip.CRC32;

public class UdpDataSender {

    // Метод для вычисления CRC32 контрольной суммы
    public static long calculateCrc32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    // Метод для отправки данных через UDP с контрольной суммой
    public static void sendUdpDataWithChecksum(byte[] data) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            InetAddress address = InetAddress.getByName("localhost");
            int port = 50051;

            // Вычисляем CRC32 контрольную сумму данных
            long checksum = calculateCrc32(data);

            // Отправляем данные
            int chunkSize = 1024;
            byte[] ackBuffer = new byte[1024];

            for (int i = 0; i < data.length; i += chunkSize) {
                byte[] chunk = new byte[Math.min(chunkSize, data.length - i)];
                System.arraycopy(data, i, chunk, 0, chunk.length);

                // Отправляем сам чанк
                DatagramPacket packet = new DatagramPacket(chunk, chunk.length, address, port);
                socket.send(packet);

                // Отправляем контрольную сумму для этого чанка
                DatagramPacket checksumPacket = new DatagramPacket(
                        Long.toString(checksum).getBytes(),
                        Long.toString(checksum).length(),
                        address,
                        port
                );
                socket.send(checksumPacket);

                // Ждем подтверждение (ACK) от сервера
                socket.setSoTimeout(1000); // Устанавливаем таймаут в 1 секунду
                DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                try {
                    socket.receive(ackPacket);
                    String ack = new String(ackPacket.getData(), 0, ackPacket.getLength());
                    if ("ACK".equals(ack)) {
                        System.out.println("Chunk " + i + " ACK received");
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("Chunk " + i + " ACK not received, retrying...");
                    // Вы можете добавить логику для повторной отправки чанка, если необходимо
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public static void main(String[] args) {
        // Пример использования
        byte[] data = "Hello, World! This is a test.".getBytes();
        sendUdpDataWithChecksum(data);
    }
}