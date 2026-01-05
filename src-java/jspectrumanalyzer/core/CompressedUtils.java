package jspectrumanalyzer.core;

import com.github.luben.zstd.Zstd;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class CompressedUtils {
    public static byte[] compressFloatArray(float[] floatArray) {
        // 1. Конвертация float[] в byte[]
        ByteBuffer byteBuffer = ByteBuffer.allocate(floatArray.length * 4); // 4 байта на float
        for (float value : floatArray) {
            byteBuffer.putFloat(value); // Заполняем ByteBuffer значениями float
        }

        // 2. Сжатие с использованием ZSTD
        byte[] compressedData = Zstd.compress(byteBuffer.array(), 6);

        // 3. Создаем ByteBuffer для хранения размера и сжатых данных
        ByteBuffer resultBuffer = ByteBuffer.allocate(4 + compressedData.length); // 4 байта для размера
        resultBuffer.putInt(floatArray.length); // Сохраняем размер массива в первые 4 байта
        resultBuffer.put(compressedData); // Сохраняем сжатые данные

        // Возвращаем массив с размером и сжатыми данными
        return resultBuffer.array();
    }

      /*
              * Сжимает данные с использованием алгоритма Zstd.
            *
            * @param bytes исходный массив байт
     * @param level уровень компрессии (например, 19)
     * @param logger опциональный логгер для вывода ошибок
     * @return сжатый массив байт, где первые 4 байта содержат длину исходных данных, либо null в случае ошибки
     */
    public static byte[] compressionBytes(byte[] bytes, int level) {
        try {
            // Преобразуем размер исходного массива в 4-байтовое представление
            byte[] sizeBytes = ByteBuffer.allocate(4).putInt(bytes.length).array();
            // Сжимаем данные с использованием Zstd
            byte[] compressedBytes = Zstd.compress(bytes, level);
            // Объединяем размер и сжатые данные
            byte[] result = new byte[sizeBytes.length + compressedBytes.length];
            System.arraycopy(sizeBytes, 0, result, 0, sizeBytes.length);
            System.arraycopy(compressedBytes, 0, result, sizeBytes.length, compressedBytes.length);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

       /*
               * Распаковывает данные, предварительно извлекая исходный размер из первых 4-х байт.
            *
            * @param bytes массив байт, содержащий сначала 4 байта с оригинальным размером данных, затем сжатые данные
     * @param logger опциональный логгер для вывода ошибок
     * @return распакованный массив байт или null, если произошла ошибка или длина данных некорректна
     */
    public static byte[] uncompressedBytes(byte[] bytes) {
        try {
            // Проверка, что массив содержит как минимум 4 байта для хранения размера
            if (bytes == null || bytes.length < 4) {
                return null;
            }

            // Извлекаем оригинальный размер (первые 4 байта)
            int originalSize = ByteBuffer.wrap(bytes, 0, 4).getInt();
            if (originalSize <= 0) {
                return null;
            }

            // Извлекаем сжатые данные
            byte[] compressedData = Arrays.copyOfRange(bytes, 4, bytes.length);
            // Распаковываем данные с использованием Zstd
            return Zstd.decompress(compressedData, originalSize);
        } catch (Exception e) {
            return null;
        }
    }
}
