package jspectrumanalyzer.socket;

public class ByteArrayUtils {
    public static byte[] toCurrentByteBufferFromSocketIO(byte[] input) {
        // Проверка на null и пустой массив
        if (input == null || input.length <= 1) {
            return null;
        }
        // Создание нового массива, копируя байты начиная со второго
        byte[] result = new byte[input.length - 1];
        System.arraycopy(input, 1, result, 0, result.length);
        return result;
    }
}
