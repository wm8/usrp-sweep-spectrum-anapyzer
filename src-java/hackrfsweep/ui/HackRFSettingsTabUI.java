package hackrfsweep.ui;

import jspectrumanalyzer.HackRFSweepSpectrumAnalyzer;
import jspectrumanalyzer.core.HackRFSettings;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;
import shared.mvc.MVCController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class HackRFSettingsTabUI {

    private final HackRFSettings hRF;

    public HackRFSettingsTabUI(HackRFSettings hackRFSettings) {
        hRF = hackRFSettings;
    }

    private JSlider sliderGain;
    private JSlider slider_waterfallPaletteStart;
    private JSlider slider_waterfallPaletteSize;
    private JSlider sliderGainVGA;
    private JSlider sliderGainLNA;
    private JCheckBox chckbxAntennaLNA;
    @Getter
    private JPanel panel;

    private JPanel hackRfOptionsTab;


    public final String getTabName() {
        return "Опции HackRF";
    }

    public JPanel initHackRFSettingsTab() {
        hackRfOptionsTab	= new JPanel(new MigLayout("", "[123.00px,grow,leading]", "[][][0][][][0][][][0][][][0][][][0][][0][][grow,fill]"));
        hackRfOptionsTab.setForeground(Color.WHITE);
        hackRfOptionsTab.setBackground(Color.BLACK);

        /*JLabel lblGain = new JLabel("Усиление [dB]");
        lblGain.setForeground(Color.WHITE);
        hackRfOptionsTab.add(lblGain, "cell 0 0");


        sliderGain = new JSlider(JSlider.HORIZONTAL, 0, 100, 2);
        sliderGain.setFont(new Font("Monospaced", Font.BOLD, 16));
        sliderGain.setBackground(Color.BLACK);
        sliderGain.setForeground(Color.WHITE);
        hackRfOptionsTab.add(sliderGain, "flowy,cell 0 1,growx");
        new MVCController(sliderGain, hRF.getGain());
        */

        JLabel lbl_gainValue = new JLabel(hRF.getGain() + "dB");
        lbl_gainValue.setForeground(Color.WHITE);
        hackRfOptionsTab.add(lbl_gainValue, "cell 0 1,alignx right");

        JLabel lblNewLabel_2 = new JLabel("LNA Усиление [dB]");
        lblNewLabel_2.setForeground(Color.WHITE);
        hackRfOptionsTab.add(lblNewLabel_2, "cell 0 3");

        sliderGainLNA = new JSlider(SwingConstants.HORIZONTAL, 0, 100, 2);
        sliderGainLNA.setForeground(Color.WHITE);
        sliderGainLNA.setFont(new Font("Monospaced", Font.BOLD, 16));
        sliderGainLNA.setBackground(Color.BLACK);


        JButton leftLNAButton = new JButton("←");
        JButton rightLNAButton = new JButton("→");

        leftLNAButton.addActionListener((ActionEvent e) -> {
            sliderGainLNA.setValue(sliderGainLNA.getValue() - 1);
        });

        rightLNAButton.addActionListener((ActionEvent e) -> {
            sliderGainLNA.setValue(sliderGainLNA.getValue() + 1);
        });

        JPanel panelLNA = new JPanel(new BorderLayout());
        panelLNA.setOpaque(false); // Делаем панель прозрачной
        panelLNA.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15)); // Отступы по 10 пикселей
        panelLNA.add(leftLNAButton, BorderLayout.WEST);
        panelLNA.add(sliderGainLNA, BorderLayout.CENTER);
        panelLNA.add(rightLNAButton, BorderLayout.EAST);

        hackRfOptionsTab.add(panelLNA, "cell 0 4,growx");

        sliderGainLNA.setModel(new DefaultBoundedRangeModel(hRF.getGainLNA().getValue(), 0, hRF.getGainLNA().getMin(), hRF.getGainLNA().getMax()));
        sliderGainLNA.setSnapToTicks(true);
        sliderGainLNA.setMinorTickSpacing(hRF.getGainLNA().getStep());
        new MVCController(sliderGainLNA, hRF.getGainLNA());


        JLabel lblVgfaGaindb = new JLabel("VGA Усиление [dB]");
        lblVgfaGaindb.setForeground(Color.WHITE);
        hackRfOptionsTab.add(lblVgfaGaindb, "cell 0 6");

        sliderGainVGA = new JSlider(SwingConstants.HORIZONTAL, 0, 100, 2);
        sliderGainVGA.setForeground(Color.WHITE);
        sliderGainVGA.setFont(new Font("Monospaced", Font.BOLD, 16));
        sliderGainVGA.setBackground(Color.BLACK);

        JButton leftVGAButton = new JButton("←");
        JButton rightVGAButton = new JButton("→");

        leftVGAButton.addActionListener((ActionEvent e) -> {
            sliderGainVGA.setValue(sliderGainVGA.getValue() - 1);
        });

        rightVGAButton.addActionListener((ActionEvent e) -> {
            sliderGainVGA.setValue(sliderGainVGA.getValue() + 1);
        });

        JPanel panelVGA = new JPanel(new BorderLayout());
        panelVGA.setOpaque(false); // Делаем панель прозрачной
        panelVGA.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15)); // Отступы по 10 пикселей
        panelVGA.add(leftVGAButton, BorderLayout.WEST);
        panelVGA.add(sliderGainVGA, BorderLayout.CENTER);
        panelVGA.add(rightVGAButton, BorderLayout.EAST);
        hackRfOptionsTab.add(panelVGA, "cell 0 7,growx");
        sliderGainVGA.setModel(new DefaultBoundedRangeModel(hRF.getGainVGA().getValue(), 0, hRF.getGainVGA().getMin(), hRF.getGainVGA().getMax()));

        sliderGainVGA.setSnapToTicks(true);
        sliderGainVGA.setMinorTickSpacing(hRF.getGainVGA().getStep());

        new MVCController(sliderGainVGA, hRF.getGainVGA());


        JLabel lblWaterfallPaletteStart = new JLabel("Старт палитры водопада [dB]");
        lblWaterfallPaletteStart.setForeground(Color.WHITE);
        hackRfOptionsTab.add(lblWaterfallPaletteStart, "cell 0 8");

        slider_waterfallPaletteStart = new JSlider();
        slider_waterfallPaletteStart.setForeground(Color.WHITE);
        slider_waterfallPaletteStart.setBackground(Color.BLACK);
        slider_waterfallPaletteStart.setMinimum(-100);
        slider_waterfallPaletteStart.setMaximum(0);
        slider_waterfallPaletteStart.setValue(-30);

        JButton leftPaletteStartButton = new JButton("←");
        JButton rightPaletteStartButton = new JButton("→");

        leftPaletteStartButton.addActionListener((ActionEvent e) -> {
            slider_waterfallPaletteStart.setValue(slider_waterfallPaletteStart.getValue() - 1);
        });

        rightPaletteStartButton.addActionListener((ActionEvent e) -> {
            slider_waterfallPaletteStart.setValue(slider_waterfallPaletteStart.getValue() + 1);
        });

        JPanel panelPaletteStart = new JPanel(new BorderLayout());
        panelPaletteStart.setOpaque(false); // Делаем панель прозрачной
        panelPaletteStart.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15)); // Отступы по 10 пикселей
        panelPaletteStart.add(leftPaletteStartButton, BorderLayout.WEST);
        panelPaletteStart.add(slider_waterfallPaletteStart, BorderLayout.CENTER);
        panelPaletteStart.add(rightPaletteStartButton, BorderLayout.EAST);

        hackRfOptionsTab.add(panelPaletteStart, "cell 0 9,growx");

        new MVCController(slider_waterfallPaletteStart, hRF.getSpectrumPaletteStart());


        JLabel lblWaterfallPaletteLength = new JLabel("Размер палитры водопада [dB]");
        lblWaterfallPaletteLength.setForeground(Color.WHITE);
        hackRfOptionsTab.add(lblWaterfallPaletteLength, "cell 0 10");


        slider_waterfallPaletteSize = new JSlider(HackRFSweepSpectrumAnalyzer.SPECTRUM_PALETTE_SIZE_MIN, 100);
        slider_waterfallPaletteSize.setBackground(Color.BLACK);
        slider_waterfallPaletteSize.setForeground(Color.WHITE);


        JButton leftPaletteSizeButton = new JButton("←");
        JButton rightPaletteSizeButton = new JButton("→");

        leftPaletteSizeButton.addActionListener((ActionEvent e) -> {
            slider_waterfallPaletteSize.setValue(slider_waterfallPaletteSize.getValue() - 1);
        });

        rightPaletteSizeButton.addActionListener((ActionEvent e) -> {
            slider_waterfallPaletteSize.setValue(slider_waterfallPaletteSize.getValue() + 1);
        });

        JPanel panelPaletteSize = new JPanel(new BorderLayout());
        panelPaletteSize.setOpaque(false); // Делаем панель прозрачной
        panelPaletteSize.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15)); // Отступы по 10 пикселей
        panelPaletteSize.add(leftPaletteSizeButton, BorderLayout.WEST);
        panelPaletteSize.add(slider_waterfallPaletteSize, BorderLayout.CENTER);
        panelPaletteSize.add(rightPaletteSizeButton, BorderLayout.EAST);

        hackRfOptionsTab.add(panelPaletteSize, "cell 0 11,growx");
        new MVCController(slider_waterfallPaletteSize, hRF.getSpectrumPaletteSize());



        JLabel lblLNAEnable = new JLabel("Антенное усиление +14dB");
        lblLNAEnable.setForeground(Color.WHITE);
        hackRfOptionsTab.add(lblLNAEnable, "flowx,cell 0 14,growx");

        chckbxAntennaLNA = new JCheckBox("");
        chckbxAntennaLNA.setHorizontalTextPosition(SwingConstants.LEADING);
        chckbxAntennaLNA.setBackground(Color.BLACK);
        chckbxAntennaLNA.setForeground(Color.WHITE);
        hackRfOptionsTab.add(chckbxAntennaLNA, "cell 0 14,alignx right");
        new MVCController(chckbxAntennaLNA, hRF.getAntennaLNA());


        hRF.getGain().addListener((gain) -> lbl_gainValue.setText(String.format(" %ddB  [LNA: %ddB  VGA: %ddB]",
                gain, hRF.getGainLNA().getValue(), hRF.getGainVGA().getValue())));


        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(panel);

        scrollPane.setPreferredSize(new Dimension(100, 150));
        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.add(scrollPane, BorderLayout.CENTER);
        hackRfOptionsTab.add(outerPanel,"flowx,cell 0 17,growx");

			/*JLabel lblAntennaPower = new JLabel("Поляризация антенны");
			lblAntennaPower.setForeground(Color.WHITE);
			tab1.add(lblAntennaPower, "flowx,cell 0 18,growx");

			chckbxAntennaPower = new JCheckBox("");
			chckbxAntennaPower.setHorizontalTextPosition(SwingConstants.LEADING);
			chckbxAntennaPower.setBackground(Color.BLACK);
			chckbxAntennaPower.setForeground(Color.WHITE);
			tab1.add(chckbxAntennaPower, "cell 0 18,alignx right");
			Label labelVersion = new Label("Версия: "+ Version.version);
			tab1.add(labelVersion, "flowx,cell 0 19");*/
        return hackRfOptionsTab;
    }
}
