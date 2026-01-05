package jspectrumanalyzer.ui;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;

public class AzimuthPanel extends JPanel {
    private LinkedList<AzimuthValue> azimuthValues;
    private Image offScreenImage;  // Буфер для двойной буферизации
    private Dimension offScreenSize;  // Размер буфера
    private static final int MAX_VALUES = 10; // Максимальное количество отображаемых значений
    private final int azimuthSpeed = 1;
    public int frameDelay = 24;
    private int range = 1;
    private int panelHeight;

    public LinkedList<AzimuthValue> getAzimuthValues() {
        return azimuthValues;
    }

    public AzimuthPanel(int panelHeight) {
        azimuthValues = new LinkedList<>();
        this.panelHeight = panelHeight;
        //this.setPreferredSize(new Dimension(300, 200)); // Установите нужный размер
        setPreferredSize(new Dimension(30, panelHeight));  // Задаем размеры панели
        setBackground(Color.BLACK);
    }

    public void getRange(Integer startFreq, Integer endFreq){
        this.range = (endFreq - startFreq)/100;
        if (range < 1)
            frameDelay = 13;
        else
            frameDelay = 24 * range;
    }

    public void addAzimuthValue(int azimuth) {
        /*if (azimuthValues.size() >= MAX_VALUES) {
            azimuthValues.removeLast(); // Удаляем старейшее значение, если превышен лимит
        }*/
        // Добавляем новое значение сверху (с y = 0)

        int spacing = panelHeight / MAX_VALUES;

        // Проверяем, есть ли место для нового значения сверху
        if (!azimuthValues.isEmpty() && azimuthValues.getFirst().yPosition < spacing) {
            return; // Если сверху нет достаточно места, не добавляем новое значение
        }
        azimuthValues.addFirst(new AzimuthValue(azimuth, 0));
        //updateYPositions(); // Обновляем позиции y
    }

    public void updateYPositions() {
        for (AzimuthValue value : azimuthValues) {
            value.yPosition += azimuthSpeed;  // Плавно перемещаем значение вниз
        }
        // Удаляем значения, которые вышли за пределы экрана
        if (!azimuthValues.isEmpty() && azimuthValues.getLast().yPosition > getHeight()) {
            azimuthValues.removeLast();
        }
        repaint();
    }

    private void drawAzimuths(Graphics g) {
        int xOffset = getWidth() - 10;  // Отступ для рисования азимутов справа
        int spacing = panelHeight / MAX_VALUES;
        AzimuthValue previousValue = null;  // Переменная для хранения предыдущего значения
        for (AzimuthValue value : azimuthValues) {
            if (previousValue != null && value.azimuth == previousValue.azimuth) {
            // Рисуем фон, если два значения подряд одинаковые
            g.setColor(new Color(11, 18, 218));  // Пример: темно-синий фон
            g.fillRect(0, value.yPosition - spacing, getWidth(), spacing + 5);
        }
            g.setColor(Color.WHITE);
            g.drawString(String.valueOf(value.azimuth), 3, value.yPosition);  // Рисуем азимуты справа
            // Обновляем переменную предыдущего значения
            previousValue = value;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Color.BLUE); // Цвет линий
        if (offScreenImage == null || offScreenSize == null || !offScreenSize.equals(getSize())) {
            offScreenSize = getSize();
            offScreenSize.width = 30;
            offScreenSize.getHeight();
            offScreenImage = createImage(offScreenSize.width, offScreenSize.height);
        }
        Graphics offScreenGraphics = offScreenImage.getGraphics();
        super.paintComponent(offScreenGraphics);

        drawAzimuths(offScreenGraphics);
        g.drawImage(offScreenImage, 0, 0, null);
    }

    static class AzimuthValue {
        int azimuth;
        int yPosition;

        public AzimuthValue(int azimuth, int yPosition) {
            this.azimuth = azimuth;
            this.yPosition = yPosition;
        }
    }
}
