package usrp.ui;

import jspectrumanalyzer.HackRFSweepSpectrumAnalyzer;
import jspectrumanalyzer.config.ReceiverCharacteristics;
import jspectrumanalyzer.core.HackRFSettings;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;
import shared.mvc.MVCController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class UsrpSettingsTabUI {

    private final HackRFSettings hRF;

    public UsrpSettingsTabUI(HackRFSettings hackRFSettings) {
        hRF = hackRFSettings;
    }

    private JSlider slider_waterfallPaletteStart;
    private JSlider slider_waterfallPaletteSize;
    private JSlider sliderGain;
    private JCheckBox chckbxAntennaLNA;
    @Getter
    private JPanel panel;

    private JPanel usrpOptionsTab;


    public final String getTabName() {
        return "Опции Usrp";
    }

    public JPanel initUsrpSettingsTab() {
        usrpOptionsTab = new JPanel(new MigLayout("", "[123.00px,grow,leading]", "[][][0][][][0][][][0][][][0][][][0][][0][][grow,fill]"));
        usrpOptionsTab.setForeground(Color.WHITE);
        usrpOptionsTab.setBackground(Color.BLACK);

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
        JLabel gainLabel = new JLabel(String.format("Усиление [dB]: %ddB", hRF.getGain().getValue()));
        gainLabel.setForeground(Color.WHITE);
        usrpOptionsTab.add(gainLabel, "cell 0 3");

        sliderGain = new JSlider(SwingConstants.HORIZONTAL, ReceiverCharacteristics.MIN_GAIN_DB,
                ReceiverCharacteristics.MAX_GAIN_DB, ReceiverCharacteristics.START_GAIN_DB);
        sliderGain.setForeground(Color.WHITE);
        sliderGain.setFont(new Font("Monospaced", Font.BOLD, 16));
        sliderGain.setBackground(Color.BLACK);


        JButton leftLNAButton = new JButton("←");
        JButton rightLNAButton = new JButton("→");

        leftLNAButton.addActionListener((ActionEvent e) -> {
            sliderGain.setValue(sliderGain.getValue() - 1);
        });

        rightLNAButton.addActionListener((ActionEvent e) -> {
            sliderGain.setValue(sliderGain.getValue() + 1);
        });

        JPanel panelLNA = new JPanel(new BorderLayout());
        panelLNA.setOpaque(false); // Делаем панель прозрачной
        panelLNA.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15)); // Отступы по 10 пикселей
        panelLNA.add(leftLNAButton, BorderLayout.WEST);
        panelLNA.add(sliderGain, BorderLayout.CENTER);
        panelLNA.add(rightLNAButton, BorderLayout.EAST);

        usrpOptionsTab.add(panelLNA, "cell 0 4,growx");

        sliderGain.setModel(new DefaultBoundedRangeModel(
                hRF.getGain().getValue(), 0, hRF.getGain().getMin(), hRF.getGain().getMax()));
        sliderGain.setSnapToTicks(true);
        sliderGain.setMinorTickSpacing(hRF.getGain().getStep());
        new MVCController(sliderGain, hRF.getGain());


        JLabel lblWaterfallPaletteStart = new JLabel("Старт палитры водопада [dB]");
        lblWaterfallPaletteStart.setForeground(Color.WHITE);
        usrpOptionsTab.add(lblWaterfallPaletteStart, "cell 0 8");

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

        usrpOptionsTab.add(panelPaletteStart, "cell 0 9,growx");

        new MVCController(slider_waterfallPaletteStart, hRF.getSpectrumPaletteStart());


        JLabel lblWaterfallPaletteLength = new JLabel("Размер палитры водопада [dB]");
        lblWaterfallPaletteLength.setForeground(Color.WHITE);
        usrpOptionsTab.add(lblWaterfallPaletteLength, "cell 0 10");


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

        usrpOptionsTab.add(panelPaletteSize, "cell 0 11,growx");
        new MVCController(slider_waterfallPaletteSize, hRF.getSpectrumPaletteSize());



        JLabel lblLNAEnable = new JLabel("Антенное усиление +14dB");
        lblLNAEnable.setForeground(Color.WHITE);
        usrpOptionsTab.add(lblLNAEnable, "flowx,cell 0 14,growx");

        chckbxAntennaLNA = new JCheckBox("");
        chckbxAntennaLNA.setHorizontalTextPosition(SwingConstants.LEADING);
        chckbxAntennaLNA.setBackground(Color.BLACK);
        chckbxAntennaLNA.setForeground(Color.WHITE);
        usrpOptionsTab.add(chckbxAntennaLNA, "cell 0 14,alignx right");
        new MVCController(chckbxAntennaLNA, hRF.getAntennaLNA());


        hRF.getGain().addListener((gain) ->
                gainLabel.setText(String.format("Усиление [dB]: %ddB", gain)));


        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(panel);

        scrollPane.setPreferredSize(new Dimension(100, 150));
        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.add(scrollPane, BorderLayout.CENTER);
        usrpOptionsTab.add(outerPanel,"flowx,cell 0 17,growx");

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
        return usrpOptionsTab;
    }
}
