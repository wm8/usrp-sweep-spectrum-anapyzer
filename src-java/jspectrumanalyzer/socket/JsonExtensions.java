package jspectrumanalyzer.socket;

import org.json.JSONArray;

public class JsonExtensions {
    public static byte[] toByteArray(JSONArray jsonArray) {
        if (jsonArray == null || jsonArray.length() == 0) {
            return null; // Возвращаем null, если массив пустой
        }

        byte[] byteArray = new byte[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            // Получаем элемент и проверяем, является ли он целым числом
            if (!jsonArray.isNull(i)) {
                int value = jsonArray.optInt(i, -9999999);
                // Убеждаемся, что значение находится в допустимом диапазоне
                if (value != -9999999) {
                    byteArray[i] = (byte) value;
                } else {
                     return null;
                }
            }
        }
        return byteArray;
    }
}