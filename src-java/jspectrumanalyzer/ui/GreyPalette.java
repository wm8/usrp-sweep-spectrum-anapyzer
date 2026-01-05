package jspectrumanalyzer.ui;

import java.awt.*;

public class GreyPalette implements ColorPalette{


    private Color	colors[];

    private static final String INIT_GRADIENT = generateGradient();

    // Метод для генерации градиента
    private static String generateGradient() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= 255; i++) {
            sb.append(i).append("\t").append(i).append("\t").append(i).append("\r\n");
        }
        return sb.toString();
    }

    public GreyPalette()
    {
        String[] parts = INIT_GRADIENT.split("\r\n");
        colors = new Color[parts.length];
        for (int i = 0; i < parts.length; i++)
        {
            String[] rgb = parts[i].split("\\s+");
            colors[i] = new Color(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]));
        }
    }


    @Override public Color getColor(int i)
    {
        return colors[i];
    }

    @Override public Color getColorNormalized(double value)
    {
        int index = (int) (colors.length * value);
        if (index < 0)
            index = 0;
        if (index >= colors.length)
            index = colors.length - 1;
        return colors[index];
    }

    @Override public int size()
    {
        return colors.length;
    }
}
