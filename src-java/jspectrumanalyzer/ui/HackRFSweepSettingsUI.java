package jspectrumanalyzer.ui;

import io.reactivex.rxjava3.disposables.Disposable;
import jspectrumanalyzer.config.ReceiverCharacteristics;
import jspectrumanalyzer.core.*;
import jspectrumanalyzer.enciderinterface.SocketDataListener;
import jspectrumanalyzer.socket.SocketIOGadalka;
import jspectrumanalyzer.webSocket.WebSocketManager;
import net.miginfocom.swing.MigLayout;
import protoModels.FrequencyRangeProto;
import protoModels.SerifGadalkaProto;
import shared.mvc.MVCController;
import usrp.ui.UsrpSettingsTabUI;

import javax.swing.*;
import javax.swing.JSpinner.ListEditor;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.beans.PropertyChangeEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class HackRFSweepSettingsUI extends JPanel implements SerifListenner {
	/**
	 * 
	 */
	private HackRFSettings hRF;
	private SocketIOGadalka socketIO;
	private static final long serialVersionUID = 7721079457485020637L;
	private JLabel txtHackrfConnected;
	private FrequencySelectorPanel frequencySelectorStart;
	private FrequencySelectorPanel frequencySelectorEnd;
	private JSpinner spinnerFFTBinHz;
	private JSpinner spinner_numberOfSamples;
	private JCheckBox chckbxAntennaPower;
	private JCheckBox chckbxShowPeaks;
	private JCheckBox chckbxRemoveSpurs;
	private JButton btnPause;
	private SpinnerListModel spinnerModelFFTBinHz;
	private FrequencySelectorRangeBinder frequencyRangeSelector;
	private JCheckBox chckbxFilterSpectrum;
	private JSpinner spinnerPeakFallSpeed;
	private JComboBox<FrequencyAllocationTable> comboBoxFrequencyAllocationBands;
	private JLabel lblPeakFall;
	private JComboBox<BigDecimal> comboBoxLineThickness;
	private JLabel lblPersistentDisplay;
	private JCheckBox checkBoxPersistentDisplay;
	private JCheckBox checkBoxWaterfallEnabled;
	private JLabel lblDecayRate;
	private JComboBox comboBoxDecayRate;
	private JLabel lblDebugDisplay;
	private JCheckBox checkBoxDebugDisplay;
	public JSlider slider_chartForPing;
	private Integer azimuthForSerif = 0;
	private final Disposable subscription;
	private Integer testValue = 0;
	private JLabel newItem;
	private JCheckBox checkboxChartVisible;
	private JComboBox<String> comboBoxSerifVariants;
	private JComboBox<Integer> comboBoxCounterSignal;
	private JComboBox<Integer> comboBoxLoseCounter;
	private boolean isClicked = false;
	private JLabel socketStatus;
	private JLabel wsStatus;
	private JLabel isCrossLabel;
	private JCheckBox isCrossCheckBox;
	private JComboBox<Integer> comboBoxSubRange;
	private JLabel lblSubRange;


	private final UsrpSettingsTabUI usrpSettingsTabUI;
	/**
	 * Create the panel.
	 */
	public HackRFSweepSettingsUI(HackRFSettings hackRFSettings, SocketIOGadalka socket, AzimuthListener azimuthListener)
	{
		this.hRF	= hackRFSettings;
		this.socketIO = socket;

		setForeground(Color.WHITE);
		setBackground(Color.BLACK);
		int minFreq = ReceiverCharacteristics.MIN_HZ;
		int maxFreq = ReceiverCharacteristics.MAX_HZ;
		int freqStep = 1;


		JPanel panelMainSettings	= new JPanel(new MigLayout("", "[123.00px,grow,leading]", "[][][::0px][][]"));
		panelMainSettings.setBorder(new EmptyBorder(UIManager.getInsets("TabbedPane.tabAreaInsets")));;
		panelMainSettings.setBackground(Color.BLACK);

		//лейбл вывода текущего азимута
		JLabel lblSerialNum = new JLabel("Текущий Азимут:");
		lblSerialNum.setForeground(Color.WHITE);
		panelMainSettings.add(lblSerialNum, "cell 0 0,growx,aligny center");

		JLabel currentAzimutLable = new JLabel("");
		currentAzimutLable.setForeground(Color.WHITE);
		panelMainSettings.add(currentAzimutLable, "cell 0 2,growx,aligny center");
		panelMainSettings.setFont(new Font("Arial",Font.BOLD, 50));

/*		String path = "/home/androkroker/azimut/azimut.txt";


		RxFileReader fileReader = new RxFileReader(path);

		System.out.println("start filerReader");

		fileReader.startReading();

		io.reactivex.rxjava3.functions.Consumer<String> customHandler = content -> {
//			System.out.println("azimut settings = " + content);

			SwingUtilities.invokeLater(() -> {
				int value = Integer.parseInt(content.trim());
				azimuthForSerif = value;
				currentAzimutLable.setText(content);
			});
		};

		// Создаем и запускаем слушателя
		FileListener fileListener = new FileListener(fileReader);
		fileListener.startListening(customHandler, null);*/
		SocketDataListener listener = new SocketDataListener(socketIO);

		subscription = listener.getAzimuthObservable().subscribe(
				azimuth -> {
					//azimuth = testValue;
					//Random random = new Random();

					// Генерация случайного числа от 0 до 360 включительно
					//azimuth = random.nextInt(361);
					azimuthForSerif = azimuth;
					//System.out.println("settings Azimut: " + azimuth);
					currentAzimutLable.setText(azimuth.toString());
					azimuthListener.azimuth(azimuth);
					//testValue += 2;
					//if (testValue >= 180)
					//	testValue = 0;
				},
				Throwable::printStackTrace
		);


		/*Timer updateTimer = new Timer(300, e -> {
			Random random = new Random();

			// Генерация случайного числа от 0 до 360 включительно
			int randomNumber = random.nextInt(361);
			azimuthListener.azimuth(randomNumber);
		});
		updateTimer.start();*/
//		задание диапазона частот
		JLabel spase = new JLabel(" ");
		spase.setForeground(Color.WHITE);
		panelMainSettings.add(spase,"cell 0 10,growx,aligny bottom");

//		задание диапазона частот
		JLabel lblNewLabel = new JLabel("Начальная частота [MHz]");
		lblNewLabel.setForeground(Color.WHITE);
		panelMainSettings.add(lblNewLabel,"cell 0 11,growx,aligny bottom");

		frequencySelectorStart = new FrequencySelectorPanel(minFreq, maxFreq, freqStep, minFreq);
		panelMainSettings.add(frequencySelectorStart, "cell 0 12,grow");
		frequencySelectorStart.setFocusable(false);

		JLabel lblFrequencyEndmhz = new JLabel("Конечная частота [MHz]");
		lblFrequencyEndmhz.setForeground(Color.WHITE);
		panelMainSettings.add(lblFrequencyEndmhz, "cell 0 13,alignx left,aligny center");

		frequencySelectorEnd = new FrequencySelectorPanel(minFreq, maxFreq, freqStep, maxFreq);
		panelMainSettings.add(frequencySelectorEnd, "cell 0 14,grow");
		frequencySelectorEnd.setFocusable(false);



		txtHackrfConnected = new JLabel();
		txtHackrfConnected.setText("HackRF отключен");
		txtHackrfConnected.setForeground(Color.WHITE);
		txtHackrfConnected.setBackground(Color.BLACK);
		panelMainSettings.add(txtHackrfConnected, "cell 0 22,growx");
		txtHackrfConnected.setBorder(null);


		//btnPause = new JButton("Pause");
		ImageIcon checkIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/resources/check.png")));
		ImageIcon crossIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/resources/remove.png")));

		socketStatus = new JLabel("Сокет отключен ", crossIcon, JLabel.LEFT);
		socketStatus.setHorizontalTextPosition(JLabel.LEFT);


		WebSocketManager websocket = socket.getWebSocketManager();

		websocket.addPropertyChangeListener( evt -> {
			if (evt.getNewValue() == "подключен") {
				wsStatus.setText("Вебсокет " + evt.getNewValue());
				wsStatus.setIcon(checkIcon);
			} else {
				wsStatus.setText("Вебсокет " + evt.getNewValue());
				wsStatus.setIcon(crossIcon);
			}
		});

		socketIO.addPropertyChangeListener( evt ->{
				if (evt.getNewValue() == "подключен") {
					socketStatus.setText("Сокет " + evt.getNewValue());
					socketStatus.setIcon(checkIcon);
				} else {
					socketStatus.setText("Сокет " + evt.getNewValue());
					socketStatus.setIcon(crossIcon);
				}
		});
		socketStatus.setForeground(Color.WHITE);
		socketStatus.setBackground(Color.BLACK);
		panelMainSettings.add(socketStatus, "cell 0 23,growx");

		wsStatus = new JLabel("Вебсокет отключен ", crossIcon, JLabel.LEFT);
		wsStatus.setHorizontalTextPosition(JLabel.LEFT);
		wsStatus.setForeground(Color.WHITE);
		wsStatus.setBackground(Color.BLACK);
		panelMainSettings.add(wsStatus, "cell 0 24,growx");
		
		btnPause = new JButton("Пауза");
		panelMainSettings.add(btnPause, "cell 0 25,growx");
		btnPause.setBackground(Color.black);


		InputMap inputMap = panelMainSettings.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap actionMap = panelMainSettings.getActionMap();

		inputMap.put(KeyStroke.getKeyStroke("SPACE"), "pauseAction");

		actionMap.put("pauseAction", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				btnPause.doClick(); // Имитация нажатия на кнопку
			}
		});

		panelMainSettings.setFocusable(true);
		panelMainSettings.requestFocusInWindow();

		panelMainSettings.setFocusTraversalPolicy(new DefaultFocusTraversalPolicy() {
			@Override
			public boolean accept(Component c) {
				return false; // Запрещаем всем компонентам получать фокус
			}
		});


		JTabbedPane tabbedPane	= new JTabbedPane(JTabbedPane.TOP);
		setLayout(new BorderLayout());
		add(panelMainSettings, BorderLayout.NORTH);
		add(tabbedPane, BorderLayout.CENTER);
		tabbedPane.setForeground(Color.WHITE);
		tabbedPane.setBackground(Color.BLACK);

		usrpSettingsTabUI = new UsrpSettingsTabUI(hRF);
		JPanel usrpSettingsTab = usrpSettingsTabUI.initUsrpSettingsTab();

		JPanel GraphOptionsTab	= new JPanel(new MigLayout("", "[123.00px,grow,leading]", "[][0][][][0][][][0][][0][][][0][][0][][][0][0][][][0][][0][grow,fill]"));
		GraphOptionsTab.setForeground(Color.WHITE);
		GraphOptionsTab.setBackground(Color.BLACK);
		
		tabbedPane.addTab(usrpSettingsTabUI.getTabName(), usrpSettingsTab);
		tabbedPane.addTab("Опции графика ", GraphOptionsTab);
		tabbedPane.setForegroundAt(1, Color.BLACK);
		tabbedPane.setBackgroundAt(1, Color.WHITE);

		tabbedPane.setForegroundAt(0, Color.BLACK);
		tabbedPane.setBackgroundAt(0, Color.WHITE);

		//tab1


		initGraphSettingsTab(GraphOptionsTab);

		bindViewToModel();
		if (Objects.equals(socket.getIsConnected(), "подключен")){
			socketStatus.setText("Сокет " + socket.getIsConnected());
			socketStatus.setIcon(checkIcon);
		}

		comboBoxSerifVariants.addItemListener(e -> {
			if (e.getStateChange() == ItemEvent.SELECTED) {
				boolean visible = "Мульти максимум".equals(e.getItem());
				lblSubRange.setVisible(visible);
				comboBoxSubRange.setVisible(visible);
				GraphOptionsTab.revalidate(); // Перерисовка компонента
				GraphOptionsTab.repaint();
			}
		});

	}

	private void initGraphSettingsTab(JPanel GraphOptionsTab) {
		chckbxFilterSpectrum = new JCheckBox("Filter spectrum");
		chckbxFilterSpectrum.setBackground(Color.BLACK);
		chckbxFilterSpectrum.setForeground(Color.WHITE);

		JLabel lblWaterfallEnabled = new JLabel("Водопад включен");

		lblWaterfallEnabled.setForeground(Color.WHITE);
		//GraphOptionsTab.add(lblWaterfallEnabled, "flowx,cell 0 0,growx");

		checkBoxWaterfallEnabled = new JCheckBox("");
		checkBoxWaterfallEnabled.setForeground(Color.WHITE);
		checkBoxWaterfallEnabled.setBackground(Color.BLACK);
		//GraphOptionsTab.add(checkBoxWaterfallEnabled, "cell 0 0,alignx right");

		JLabel lblChartVisible = new JLabel("График включен");
		lblChartVisible.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblChartVisible, "flowx,cell 0 0,growx");

		checkboxChartVisible = new JCheckBox("");
		checkboxChartVisible.setForeground(Color.WHITE);
		checkboxChartVisible.setBackground(Color.BLACK);
		GraphOptionsTab.add(checkboxChartVisible,"cell 0 0,alignx right");

		JLabel lblSerifVariants = new JLabel("Вариант детекции");
		lblSerifVariants.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblSerifVariants, "cell 0 1,growx");

		comboBoxSerifVariants = new JComboBox(new String[] {
                "Ручная", "Середина", "Максимум", "Мульти максимум"
		});
		GraphOptionsTab.add(comboBoxSerifVariants, "cell 0 1,alignx right");

		lblSubRange = new JLabel("Размер поддиапазонов");
		lblSubRange.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblSubRange, "cell 0 2,growx");

		comboBoxSubRange = new JComboBox(new Integer[] {
				5,10,20,40,100
		});
		GraphOptionsTab.add(comboBoxSubRange, "cell 0 2,alignx right");

		//comboBoxSubRange.setVisible(false);

		JLabel lblChartminimumPing = new JLabel("Минимальная линия для засечки");
		lblWaterfallEnabled.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblChartminimumPing, "cell 0 3");

		slider_chartForPing = new JSlider();
		slider_chartForPing.setForeground(Color.WHITE);
		slider_chartForPing.setBackground(Color.BLACK);
		slider_chartForPing.setMinimum(-100);
		slider_chartForPing.setMaximum(0);
		slider_chartForPing.setValue(-40);


		JButton leftMinLineButton = new JButton("←");
		JButton rightMinLineButton = new JButton("→");

		leftMinLineButton.addActionListener((ActionEvent e) -> {
			slider_chartForPing.setValue(slider_chartForPing.getValue() - 1);
		});

		rightMinLineButton.addActionListener((ActionEvent e) -> {
			slider_chartForPing.setValue(slider_chartForPing.getValue() + 1);
		});

		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false); // Делаем панель прозрачной
		panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15)); // Отступы по 10 пикселей
		panel.add(leftMinLineButton, BorderLayout.WEST);
		panel.add(slider_chartForPing, BorderLayout.CENTER);
		panel.add(rightMinLineButton, BorderLayout.EAST);

		GraphOptionsTab.add(panel, "cell 0 4,growx");

		JLabel lblFftBinhz = new JLabel("Ширина полосы диапазона");
		lblFftBinhz.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblFftBinhz, "cell 0 5");

		spinnerFFTBinHz = new JSpinner();
		spinnerFFTBinHz.setFont(new Font("Monospaced", Font.BOLD, 16));
		spinnerModelFFTBinHz = new SpinnerListModel(new String[] { "1 000", "2 000", "5 000", "10 000", "20 000",
				"50 000", "100 000", "200 000", "500 000", "1 000 000", "2 000 000", "5 000 000" });
		spinnerFFTBinHz.setModel(spinnerModelFFTBinHz);
		GraphOptionsTab.add(spinnerFFTBinHz, "cell 0 5,growx");
		((ListEditor) spinnerFFTBinHz.getEditor()).getTextField().setHorizontalAlignment(JTextField.RIGHT);

		JLabel lblNumberOfSamples = new JLabel("Баланс детализации");
		lblNumberOfSamples.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblNumberOfSamples, "cell 0 6");

		spinner_numberOfSamples = new JSpinner();
		spinner_numberOfSamples.setModel(new SpinnerListModel(new String[] { "1024", "2048", "4096", "8192", "16384", "32768", "65536", "131072", "262144" }));
		spinner_numberOfSamples.setFont(new Font("Monospaced", Font.BOLD, 16));
		((ListEditor) spinner_numberOfSamples.getEditor()).getTextField().setHorizontalAlignment(JTextField.RIGHT);
		((ListEditor) spinner_numberOfSamples.getEditor()).getTextField().setEditable(false);
		GraphOptionsTab.add(spinner_numberOfSamples, "cell 0 6,growx");


		/*JLabel lblWaterfallPaletteStart = new JLabel("Старт палитры водопада [dB]");
		lblWaterfallPaletteStart.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblWaterfallPaletteStart, "cell 0 4");

		slider_waterfallPaletteStart = new JSlider();
		slider_waterfallPaletteStart.setForeground(Color.WHITE);
		slider_waterfallPaletteStart.setBackground(Color.BLACK);
		slider_waterfallPaletteStart.setMinimum(-100);
		slider_waterfallPaletteStart.setMaximum(0);
		slider_waterfallPaletteStart.setValue(-30);
		GraphOptionsTab.add(slider_waterfallPaletteStart, "cell 0 5,growx");


		JLabel lblWaterfallPaletteLength = new JLabel("Размер палитры водопада [dB]");
		lblWaterfallPaletteLength.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblWaterfallPaletteLength, "cell 0 6");

		slider_waterfallPaletteSize = new JSlider(HackRFSweepSpectrumAnalyzer.SPECTRUM_PALETTE_SIZE_MIN, 100);
		slider_waterfallPaletteSize.setBackground(Color.BLACK);
		slider_waterfallPaletteSize.setForeground(Color.WHITE);
		GraphOptionsTab.add(slider_waterfallPaletteSize, "cell 0 7,growx");*/


		JLabel lblLoseCounter = new JLabel("Каунтер потери сигнала");
		lblLoseCounter.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblLoseCounter, "flowx,cell 0 8,growx");

		comboBoxLoseCounter = new JComboBox<>(new Integer[] {
				20,30,40,50
		});
		GraphOptionsTab.add(comboBoxLoseCounter, "cell 0 8,alignx right");

		JLabel lblTest = new JLabel("кол-во итераций сигнала");
		lblTest.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblTest, "flowx,cell 0 9,growx");

		comboBoxCounterSignal = new JComboBox(new Integer[] {
                5, 10, 15, 20, 25, 30
		});
		GraphOptionsTab.add(comboBoxCounterSignal, "cell 0 9,alignx right");

		JLabel lblShowPeaks = new JLabel("Показывать пик");
		lblShowPeaks.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblShowPeaks, "flowx,cell 0 10,growx");


		chckbxShowPeaks = new JCheckBox("");
		chckbxShowPeaks.setForeground(Color.WHITE);
		chckbxShowPeaks.setBackground(Color.BLACK);
		GraphOptionsTab.add(chckbxShowPeaks, "cell 0 10,alignx right");

		lblPeakFall = new JLabel("  Скорость падения [s]");
		lblPeakFall.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblPeakFall, "flowx,cell 0 11,growx");

		spinnerPeakFallSpeed = new JSpinner();
		spinnerPeakFallSpeed.setModel(new SpinnerNumberModel(10, 0, 500, 1));
		GraphOptionsTab.add(spinnerPeakFallSpeed, "cell 0 11,alignx right");

		JLabel lblSpurFiltermay = new JLabel("Убрать помехи");
		lblSpurFiltermay.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblSpurFiltermay, "flowx,cell 0 13,growx");

		chckbxRemoveSpurs = new JCheckBox("");
		chckbxRemoveSpurs.setForeground(Color.WHITE);
		chckbxRemoveSpurs.setBackground(Color.BLACK);
		GraphOptionsTab.add(chckbxRemoveSpurs, "cell 0 13,alignx right");


		JLabel lblSpectrLineThickness = new JLabel("Толщина линии спектра");
		lblSpectrLineThickness.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblSpectrLineThickness, "flowx,cell 0 14,growx");

		comboBoxLineThickness = new JComboBox(new BigDecimal[] {
				new BigDecimal("1"), new BigDecimal("1.5"), new BigDecimal("2"), new BigDecimal("3")
		});
		GraphOptionsTab.add(comboBoxLineThickness, "cell 0 14,alignx right");


		lblPersistentDisplay = new JLabel("Постоянное отображение");
		lblPersistentDisplay.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblPersistentDisplay, "flowx,cell 0 15,growx");

		checkBoxPersistentDisplay = new JCheckBox("");
		checkBoxPersistentDisplay.setForeground(Color.WHITE);
		checkBoxPersistentDisplay.setBackground(Color.BLACK);
		GraphOptionsTab.add(checkBoxPersistentDisplay, "cell 0 15,alignx right");

		lblDecayRate = new JLabel("  Время отображения [s]");
		lblDecayRate.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblDecayRate, "flowx,cell 0 16,growx");

		comboBoxDecayRate = new JComboBox(
				new Vector<>(IntStream.rangeClosed(hRF.getPersistentDisplayDecayRate().getMin(), hRF.getPersistentDisplayDecayRate().getMax()).
						boxed().collect(Collectors.toList())));
		GraphOptionsTab.add(comboBoxDecayRate, "cell 0 16,alignx right");

		isCrossLabel = new JLabel("включить прицел");
		isCrossLabel.setForeground(Color.WHITE);
		GraphOptionsTab.add(isCrossLabel, "flowx,cell 0 17,growx");

		isCrossCheckBox = new JCheckBox("");
		isCrossCheckBox.setForeground(Color.WHITE);
		isCrossCheckBox.setBackground(Color.BLACK);
		GraphOptionsTab.add(isCrossCheckBox, "cell 0 17,alignx right");


		JLabel lblDisplayFrequencyAllocation = new JLabel("Полоса распределения частот");
		lblDisplayFrequencyAllocation.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblDisplayFrequencyAllocation, "cell 0 19");


		FrequencyAllocations frequencyAllocations	= new FrequencyAllocations();
		Vector<FrequencyAllocationTable> freqAllocValues	= new Vector<>();
		freqAllocValues.add(null);
		freqAllocValues.addAll(frequencyAllocations.getTable().values());
		DefaultComboBoxModel<FrequencyAllocationTable> freqAllocModel	= new  DefaultComboBoxModel<>(freqAllocValues);
		comboBoxFrequencyAllocationBands = new JComboBox<FrequencyAllocationTable>(freqAllocModel);
		GraphOptionsTab.add(comboBoxFrequencyAllocationBands, "cell 0 20,growx");


		lblDebugDisplay = new JLabel("Debug display");
		lblDebugDisplay.setForeground(Color.WHITE);
		GraphOptionsTab.add(lblDebugDisplay, "flowx,cell 0 22,growx");

		checkBoxDebugDisplay = new JCheckBox("");
		checkBoxDebugDisplay.setForeground(Color.WHITE);
		checkBoxDebugDisplay.setBackground(Color.BLACK);
		GraphOptionsTab.add(checkBoxDebugDisplay, "cell 0 22,alignx right");
	}



	/**
	 * Creates controllers for ui elements
	 */
	private void bindViewToModel() {
		frequencyRangeSelector = new FrequencySelectorRangeBinder(frequencySelectorStart, frequencySelectorEnd);
		frequencyRangeSelector.selFreqStart.setFocusable(false);
		frequencyRangeSelector.selFreqEnd.setFocusable(false);

		new MVCController(spinnerFFTBinHz, hRF.getFFTBinHz(),
				viewValue -> Integer.parseInt(viewValue.toString().replaceAll("\\s", "")),
				modelValue -> {
					Optional<?> val = spinnerModelFFTBinHz.getList().stream().filter(value -> modelValue <= Integer.parseInt(value.toString().replaceAll("\\s", ""))).findFirst();
					if (val.isPresent())
						return val.get();
					else
						return spinnerModelFFTBinHz.getList().get(0);
				});
		new MVCController(spinner_numberOfSamples, hRF.getSamples(), val -> Integer.parseInt(val.toString()), val -> val.toString());
		//new MVCController(chckbxAntennaPower, hRF.getAntennaPowerEnable());

		new MVCController(slider_chartForPing, hRF.linePing());
		new MVCController(	(Consumer<FrequencyRange> valueChangedCall) ->
								frequencyRangeSelector.addPropertyChangeListener((PropertyChangeEvent evt) -> valueChangedCall.accept(frequencyRangeSelector.getFrequencyRange()) ) ,
							(FrequencyRange newComponentValue) -> {
								if(frequencyRangeSelector.selFreqStart.getValue() != newComponentValue.getStartMHz())
									frequencyRangeSelector.selFreqStart.setValue(newComponentValue.getStartMHz());
								if(frequencyRangeSelector.selFreqEnd.getValue() != newComponentValue.getEndMHz())
									frequencyRangeSelector.selFreqEnd.setValue(newComponentValue.getEndMHz());
							},
							hRF.getFrequency()
		);
		new MVCController(chckbxShowPeaks, hRF.isChartsPeaksVisible());
		new MVCController(chckbxFilterSpectrum, hRF.isFilterSpectrum());
		new MVCController(chckbxRemoveSpurs, hRF.isSpurRemoval());

		new MVCController((valueChangedCall) -> btnPause.addActionListener((event) -> valueChangedCall.accept(!hRF.isCapturingPaused().getValue())),
				isCapt -> btnPause.setText(!isCapt ? "Пауза"  : "Продолжить"),
				hRF.isCapturingPaused());

		new MVCController(spinnerPeakFallSpeed, hRF.getPeakFallRate(), in -> (Integer)in, in -> in);

		new MVCController(comboBoxFrequencyAllocationBands, hRF.getFrequencyAllocationTable());

		new MVCController(comboBoxLineThickness, hRF.getSpectrumLineThickness());

		new MVCController(comboBoxSerifVariants, hRF.getSerifVariant());

		new MVCController(comboBoxSubRange, hRF.getSubRange());

		new MVCController(comboBoxCounterSignal,hRF.counterForSerif());

		new MVCController(isCrossCheckBox, hRF.isCrossEnabled());

		new MVCController(comboBoxLoseCounter,hRF.counterForLoseSignal());

		new MVCController(checkBoxPersistentDisplay, hRF.isPersistentDisplayVisible());

		new MVCController(checkBoxWaterfallEnabled, hRF.isWaterfallVisible());

		new MVCController(checkboxChartVisible, hRF.isChartVisible());

		new MVCController(checkBoxDebugDisplay, hRF.isDebugDisplay());

		hRF.isChartsPeaksVisible().addListener((enabled) -> {
			SwingUtilities.invokeLater(()->{
				spinnerPeakFallSpeed.setEnabled(enabled);
				spinnerPeakFallSpeed.setVisible(enabled);
				lblPeakFall.setVisible(enabled);
			});
		});
		hRF.isChartsPeaksVisible().callObservers();

		new MVCController(comboBoxDecayRate, hRF.getPersistentDisplayDecayRate());
		hRF.isPersistentDisplayVisible().addListener((visible) -> {
			SwingUtilities.invokeLater(()->{
				comboBoxDecayRate.setVisible(visible);
				lblDecayRate.setVisible(visible);
			});
		});
		hRF.isPersistentDisplayVisible().callObservers();

		hRF.registerListener(new HackRFSettings.HackRFEventAdapter()
		{
			@Override public void captureStateChanged(boolean isCapturing)
			{
//				btnPause.setText(isCapturing ? "Pause"  : "Resume");
			}
			@Override public void hardwareStatusChanged(boolean hardwareSendingData)
			{
				txtHackrfConnected.setText("HackRF "+(hardwareSendingData ? "подключен":"отключен"));
			}
		});;

	}

	@Override
	public void serifMessage(Integer startMHz, Integer endMHz, String hackrfid) {
		try {
			FrequencyRangeProto frequencyRangeProto = FrequencyRangeProto.newBuilder()
					.setMinFrequency(startMHz)
					.setMaxFrequency(endMHz)
					.build();
			byte [] serifMessage = SerifGadalkaProto.newBuilder()
					.setFrequencyRange(frequencyRangeProto)
					.setAzimuth(azimuthForSerif)
					.build()
					.toByteArray();
			//FrequencyRangeGadalka frequencyRange = new FrequencyRangeGadalka(startMHz, endMHz);

// Create a SerifGadalka object
			//SerifGadalka serifGadalka = new SerifGadalka(azimuthForSerif, frequencyRange);

			System.out.println("отправка засечки " + startMHz + "-" + endMHz);
			java.time.LocalDate currentDate = java.time.LocalDate.now();
			System.out.println(currentDate);

			java.time.LocalTime currentTime = java.time.LocalTime.now();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
			System.out.println(currentTime.format(formatter));
			newItem = new JLabel(currentDate + " " + currentTime.format(formatter) + " " +startMHz + "-" + endMHz + ", азимут " +azimuthForSerif);
			//TODO: what a panel????
			JPanel panel = usrpSettingsTabUI.getPanel();
			panel.add(newItem);
			panel.revalidate();  // Обновляем панель после добавления нового элемента
			panel.repaint();
			// Автоматическая прокрутка вниз
			SwingUtilities.invokeLater(() -> {
				newItem.scrollRectToVisible(newItem.getBounds());
			});
			try {
				FileWriter writer = new FileWriter("/home/user/gadalkaLogs/analizatorLogs/seriflogs/"+ hackrfid +".txt", true);
				writer.write(currentDate + " " + currentTime + "; " +"отправка засечки " + startMHz + "-" + endMHz + "; азимут " +azimuthForSerif);
				writer.append('\n');
				writer.close();
			} catch (IOException e) {
				System.out.println("Ошибка при записи в файл");
				e.printStackTrace();
			}

			socketIO.serifMessage(serifMessage);
			//System.out.println(startMHz + " - " + endMHz + " - " + serifGadalka);
		}catch (Exception e) {
			System.out.println(startMHz + " - " + endMHz + " - " + e.getMessage());
		}
	}

	@Override
	public void serifMessage2(Integer startMHz, Integer endMHz, String hackrfid, Integer azimuth, float maxDbm) {

		try {
			FrequencyRangeProto frequencyRangeProto = FrequencyRangeProto.newBuilder()
					.setMinFrequency(startMHz)
					.setMaxFrequency(endMHz)
					.build();
			byte [] serifMessage = SerifGadalkaProto.newBuilder()
					.setFrequencyRange(frequencyRangeProto)
					.setAzimuth(azimuth)
					.setDbm(maxDbm)
					.build()
					.toByteArray();
			//FrequencyRangeGadalka frequencyRange = new FrequencyRangeGadalka(startMHz, endMHz);

// Create a SerifGadalka object
			//SerifGadalka serifGadalka = new SerifGadalka(azimuthForSerif, frequencyRange);

			System.out.println("отправка засечки " + startMHz + "-" + endMHz);
				java.time.LocalDate currentDate = java.time.LocalDate.now();
			//System.out.println(currentDate);
			System.out.println(Objects.requireNonNull(comboBoxSerifVariants.getSelectedItem()));
			java.time.LocalTime currentTime = java.time.LocalTime.now();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
			System.out.println(currentTime.format(formatter));
			String varSerif = "";
			if (isClicked)
				varSerif = "Ручная";
			else
				varSerif = Objects.requireNonNull(comboBoxSerifVariants.getSelectedItem()).toString();
			newItem = new JLabel(currentTime.format(formatter) + " " +startMHz + "-" + endMHz + ", азимут " +azimuth + ", " + "максимальный Dbm " + maxDbm + ", " +  varSerif);
			//TODO: what a panel????
			JPanel panel = usrpSettingsTabUI.getPanel();
			panel.add(newItem);

			panel.revalidate();  // Обновляем панель после добавления нового элемента
			panel.repaint();
			// Автоматическая прокрутка вниз
			SwingUtilities.invokeLater(() -> {
				newItem.scrollRectToVisible(newItem.getBounds());
			});// Перерисовываем содержимое
			try {
				FileWriter writer = new FileWriter("/home/user/gadalkaLogs/analizatorLogs/seriflogs/" + hackrfid +".txt", true);
				writer.write(currentDate + " " + currentTime + "; " +"отправка засечки " + startMHz + "-" + endMHz + "; азимут " +azimuth + "максимальный Dbm " + maxDbm + "; " + " тип засечки " + varSerif);
				writer.append('\n');
				writer.close();
			} catch (IOException e) {
				System.out.println("Ошибка при записи в файл");
				e.printStackTrace();
			}
			isClicked = false;
			socketIO.serifMessage(serifMessage);
			//System.out.println(startMHz + " - " + endMHz + " - " + serifGadalka);
		}catch (Exception e) {
			System.out.println(startMHz + " - " + endMHz + " - " + e.getMessage());
		}
	}

    public void setClicked(boolean clicked) {
        isClicked = clicked;
    }

	public void visibleLabel(){
		lblSubRange.setVisible(false);
		comboBoxSubRange.setVisible(false);
	}
}
