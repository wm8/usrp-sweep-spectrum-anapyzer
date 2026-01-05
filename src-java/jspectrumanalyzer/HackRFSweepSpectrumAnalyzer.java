package jspectrumanalyzer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jspectrumanalyzer.capture.ScreenCapture;
import jspectrumanalyzer.core.*;
import jspectrumanalyzer.core.jfc.XYSeriesCollectionImmutable;
import jspectrumanalyzer.core.jfc.XYSeriesImmutable;
import jspectrumanalyzer.enciderinterface.ConditionalDataListener2;
import jspectrumanalyzer.nativebridge.HackRFSweepDataCallback;
import jspectrumanalyzer.nativebridge.HackRFSweepNativeBridge;
import jspectrumanalyzer.socket.SocketIOGadalka;
import jspectrumanalyzer.ui.AzimuthPanel;
import jspectrumanalyzer.ui.HackRFSweepSettingsUI;
import jspectrumanalyzer.ui.WaterfallPlot;
import jspectrumanalyzer.webSocket.SpectrumConfig;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisSpace;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.StandardTickUnitSource;
import org.jfree.chart.event.ChartProgressEvent;
import org.jfree.chart.event.ChartProgressListener;
import org.jfree.chart.event.OverlayChangeListener;
import org.jfree.chart.panel.Overlay;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.Range;
import org.jfree.data.xy.XYSeries;
import org.jfree.ui.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import shared.mvc.ModelValue;
import shared.mvc.ModelValue.ModelValueBoolean;
import shared.mvc.ModelValue.ModelValueInt;
import io.reactivex.rxjava3.core.Observable;


import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPOutputStream;


public class HackRFSweepSpectrumAnalyzer implements HackRFSettings, HackRFSweepDataCallback,AzimuthListener,SettingsWSListener {


	private static class PerformanceEntry{
		final String name;
		long nanosSum;
		int count;
		public PerformanceEntry(String name) {
			this.name 	= name;
		}
		public void addDrawingTime(long nanos) {
			nanosSum	+= nanos;
			count++;
		}
		public void reset() {
			count	= 0;
			nanosSum	= 0;
		}
		@Override
		public String toString() {
			return name;
		}
	}
	
	private static class RuntimePerformanceWatch {
		/**
		 * incoming full spectrum updates from the hardware
		 */
		int				hwFullSpectrumRefreshes	= 0;
		volatile long	lastStatisticsRefreshed	= System.currentTimeMillis();
		PerformanceEntry persisentDisplay	= new PerformanceEntry("Pers.disp");
		PerformanceEntry waterfallUpdate	= new PerformanceEntry("Wtrfall.upd");
		PerformanceEntry waterfallDraw	= new PerformanceEntry("Wtrfll.drw");
		PerformanceEntry chartDrawing	= new PerformanceEntry("Spectr.chart");
		PerformanceEntry spurFilter = new PerformanceEntry("Spur.fil");
		
		private ArrayList<PerformanceEntry> entries	= new ArrayList<>();
		public RuntimePerformanceWatch() {
			entries.add(persisentDisplay);
			entries.add(waterfallUpdate);
			entries.add(waterfallDraw);
			entries.add(chartDrawing);
			entries.add(spurFilter);
		}
		
		public synchronized String generateStatistics() {
			long timeElapsed = System.currentTimeMillis() - lastStatisticsRefreshed;
			if (timeElapsed <= 0)
				timeElapsed = 1;
			StringBuilder b	= new StringBuilder();
			long sumNanos	= 0;
			for (PerformanceEntry entry : entries) {
				sumNanos	+= entry.nanosSum;
				float callsPerSec	= entry.count/(timeElapsed/1000f);
				b.append(entry.name).append(String.format(" %3dms (%5.1f calls/s) \n", entry.nanosSum/1000000, callsPerSec));
			}
			b.append(String.format("Total: %4dms draw time/s: ", sumNanos/1000000));
			return b.toString();
//			double timeSpentDrawingChartPerSec = chartDrawingSum / (timeElapsed / 1000d) / 1000d;
//			return String.format("Spectrum refreshes: %d / Chart redraws: %d / Drawing time in 1 sec %.2fs",
//					hwFullSpectrumRefreshes, chartRedrawed, timeSpentDrawingChartPerSec);

		}

		public synchronized void reset() {
			hwFullSpectrumRefreshes = 0;
			for (PerformanceEntry dataDrawingEntry : entries) {
				dataDrawingEntry.reset();
			}
			lastStatisticsRefreshed = System.currentTimeMillis();
		}
	}

	/**
	 * Color palette for UI
	 */
	protected static class ColorScheme {
		Color	palette0	= Color.white;
		Color	palette1	= new Color(0xe5e5e5);
		Color	palette2	= new Color(0xFCA311);
		Color	palette3	= new Color(0x14213D);
		Color	palette4	= Color.BLACK;
	}


	public static final int	SPECTRUM_PALETTE_SIZE_MIN	= 5;
	private static boolean	captureGIF					= false;
	public static int numberApp = 0;
	public static String hackrfID = "";
	public static int freqStart = 2400;
	public static int freqEnd = 2500;
	public static int parametrGainLNA = 0;
	public static int parametrGainVGA = 0;
	public static int totalgain = 0;
	public static int samplesapp = 8192;
	public static int FFTBinHz = 100000;
	public static boolean showPeaks = false;
	public static boolean antennaLNA = false;
	public static boolean antennaPolarization = false;
	public static String  pathFile = "";
	public static int port = 7000;
	public static String uid = "";
	public static final String ipadress = "localhost";
	public static Integer counterForEmit = 0;
	public static Integer oldCounter = 0;
	public static Integer minLineForSerif = -60;
	public static Integer startWaterfallPalette = -90;
	public static Integer sizeWaterfallPalette = 65;
	public static Integer lineThickness = 1;
	public static Integer timeShowPeaks = 30;
	public static boolean isSpurFilter = false;
	public static boolean pDisplay = false;
	public static Integer timePDisplay = 30;
	public static boolean debugDisplay = false;
	public static Integer MISS_THRESHOLD = 20;   // Порог пропущенных итераций для подтверждения исчезновения сигнала
	public static Integer counterForSignal = 5;
	public static Integer subRange = 10;
	public static String serifVariant = "Ручная";
	public static boolean isAim = false;
	public static List<RangeForSerif> ranges = new ArrayList<>();
	public static List<RangeForSerif> ranges1 = new ArrayList<>();


	boolean signalDetected = false; // Указывает, виден ли сигнал
	int missedIterations = 0;       // Счётчик пропущенных итераций
	public static List<Integer> listCounterForEmit;





	private static long		initTime					= System.currentTimeMillis();

	public static void main(String[] args) throws IOException {
		//		System.out.println(new File("").getAbsolutePath();
		//args = new String[]{"/home/androkroker/config/800.json"};
//		args = new String[]{"/home/androkroker/config/1.json"};
		System.out.println(args.length);
		System.out.println("args4 = " + Arrays.toString(args));
        if (args.length > 0) {
			if (args[0].equals("capturegif")) {
				captureGIF = true;
			}
			pathFile = args[0];
				System.out.println("privet");
				try {
					Object obj = new JSONParser().parse(new FileReader(args[0]));
					JSONObject jo = (JSONObject) obj;
					JSONObject hackRFSettings = (JSONObject) jo.get("hackrfsettings");
					JSONObject chartSettings = (JSONObject) jo.get("chartsettings");
					numberApp = ((Number) jo.get("numberapp")).intValue();
					hackrfID = (String) jo.get("hackrfid");
					port = ((Number) jo.get("udpport")).intValue();
					//ipadress = (String) jo.getOrDefault("ipadress", "localhost");
					uid = String.valueOf(hackrfID.hashCode());
					freqStart = ((Number) hackRFSettings.get("freqstart")).intValue();
					freqEnd = ((Number) hackRFSettings.get("freqend")).intValue();
					parametrGainLNA = ((Number) hackRFSettings.get("gainlna")).intValue();
					parametrGainVGA = ((Number) hackRFSettings.get("gainvga")).intValue();
					startWaterfallPalette = ((Number) hackRFSettings.getOrDefault("startwaterfallpalette", startWaterfallPalette)).intValue();
					sizeWaterfallPalette = ((Number) hackRFSettings.getOrDefault("sizewaterfallpalette", sizeWaterfallPalette)).intValue();
					totalgain = ((Number) hackRFSettings.get("totalgain")).intValue();
					antennaLNA = (Boolean) hackRFSettings.get("antennalna");
					antennaPolarization = (Boolean) hackRFSettings.getOrDefault("antennapolarization",antennaPolarization);
					serifVariant = (String) chartSettings.getOrDefault("serifvariant",serifVariant);
					subRange = ((Number) chartSettings.getOrDefault("subrange", subRange)).intValue();
					minLineForSerif = ((Number) chartSettings.getOrDefault("serifline", minLineForSerif)).intValue();
					FFTBinHz = ((Number) chartSettings.get("fftbinhz")).intValue();
					samplesapp = ((Number) chartSettings.get("samples")).intValue();
					counterForSignal = ((Number) chartSettings.getOrDefault("counterserif", counterForSignal)).intValue();
					MISS_THRESHOLD = ((Number) chartSettings.getOrDefault("counterloseserif", MISS_THRESHOLD)).intValue();
					showPeaks = (Boolean) chartSettings.getOrDefault("showpeaks",showPeaks);
					timeShowPeaks = ((Number) chartSettings.getOrDefault("timeshowpeaks", timeShowPeaks)).intValue();
					isSpurFilter = (Boolean) chartSettings.getOrDefault("spurfilter", isSpurFilter);
					lineThickness = ((Number) chartSettings.getOrDefault("linethickness", lineThickness)).intValue();
					pDisplay = (Boolean) chartSettings.getOrDefault("persistencedisplay", pDisplay);
					timePDisplay = ((Number) chartSettings.getOrDefault("timepersistencedisplay", timePDisplay)).intValue();
					isAim = (Boolean) chartSettings.getOrDefault("aim",isAim);
					// полоса распределения частот
					debugDisplay = (Boolean) chartSettings.getOrDefault("debugdisplay", debugDisplay);
					ranges = (List<RangeForSerif>) jo.getOrDefault("ranges",ranges);
					for (Object array : ranges){
						JSONObject jsonObject = (JSONObject) array;
						double startFreq = (double) jsonObject.get("startfreq");
						double endFreq = (double) jsonObject.get("endfreq");
						String status = (String) jsonObject.get("status");
						ranges1.add(new RangeForSerif(startFreq,endFreq,status));
					}





				} catch (ParseException e) {
					System.out.println(e.getMessage());
				}
		}
		//		try { Thread.sleep(20000); System.out.println("Started..."); } catch (InterruptedException e) {}
		new HackRFSweepSpectrumAnalyzer();
	}


	public boolean									flagIsHWSendingData						= false;
	private float									alphaFreqAllocationTableBandsImage	= 0.5f;
	private float									alphaPersistentDisplayImage			= 1.0f;
	private JFreeChart								chart;

	private ModelValue<Rectangle2D>					chartDataArea						= new ModelValue<Rectangle2D>(
			"Chart data area", new Rectangle2D.Double(0, 0, 1, 1));
	private XYSeriesCollectionImmutable				chartDataset								= new XYSeriesCollectionImmutable();
	private XYLineAndShapeRenderer					chartLineRenderer;
	private ChartPanel								chartPanel;
	private ColorScheme								colors								= new ColorScheme();
	private DatasetSpectrumPeak						datasetSpectrum;
	private int										dropped								= 0;
	private volatile boolean						flagManualGain						= false;
	private volatile boolean						forceStopSweep						= false;
	/**
	 * Capture a GIF of the program for the GITHUB page
	 */
	private ScreenCapture							gifCap								= null;
	private ArrayList<HackRFEventListener>			hRFlisteners							= new ArrayList<>();
	private ArrayBlockingQueue<FFTBins>				hwProcessingQueue						= new ArrayBlockingQueue<>(
			100);
	private BufferedImage							imageFrequencyAllocationTableBands	= null;
	private boolean									isChartDrawing						= false;
	private ReentrantLock							lock								= new ReentrantLock();

	private ModelValueBoolean						parameterAntennaLNA   				= new ModelValueBoolean("Antenna LNA +14dB", antennaLNA);
	private ModelValueBoolean 						parameterAntennaPolarization = new ModelValueBoolean("Ant polarization", antennaPolarization);
	private ModelValueInt							parameterFFTBinHz					= new ModelValueInt("FFT Bin [Hz]", FFTBinHz);
	private ModelValueBoolean						parameterFilterSpectrum				= new ModelValueBoolean("Filter", false);
	private ModelValue<FrequencyRange>				parameterFrequency					= new ModelValue<>("Frequency range", new FrequencyRange(freqStart, freqEnd));
	private ModelValue<FrequencyAllocationTable>	parameterFrequencyAllocationTable	= new ModelValue<FrequencyAllocationTable>("Frequency allocation table", null);

	private ModelValueInt							parameterGainLNA					= new ModelValueInt("LNA Gain",parametrGainLNA, 1, 0, 40);
	private ModelValueInt							parameterGainTotal					= new ModelValueInt("Gain [dB]", totalgain);
	private ModelValueInt							parameterGainVGA					= new ModelValueInt("VGA Gain", parametrGainVGA, 1, 0, 60);
	private ModelValueBoolean						parameterIsCapturingPaused			= new ModelValueBoolean("Capturing paused", false);

	private ModelValueInt							parameterPersistentDisplayPersTime  = new ModelValueInt("Persistence time", timePDisplay, 1, 1, 60);
	private ModelValueInt							parameterPeakFallRateSecs			= new ModelValueInt("Peak fall rate", timeShowPeaks);
	private ModelValueBoolean						parameterPersistentDisplay			= new ModelValueBoolean("Persistent display", pDisplay);

	private ModelValueInt							parameterSamples					= new ModelValueInt("Samples", samplesapp);

	private ModelValueBoolean						parameterShowPeaks					= new ModelValueBoolean("Show peaks", showPeaks);

	private ModelValueBoolean 						parameterDebugDisplay				= new ModelValueBoolean("Debug", debugDisplay);
	
	private ModelValue<BigDecimal>					parameterSpectrumLineThickness		= new ModelValue<>("Spectrum line thickness", new BigDecimal(lineThickness.toString()));
	private ModelValueInt							parameterSpectrumPaletteSize		= new ModelValueInt("Spectrum palette size", sizeWaterfallPalette);
	private ModelValueInt							parameterSpectrumPaletteStart		= new ModelValueInt("Spectrum palette start", startWaterfallPalette);
	private ModelValueBoolean						parameterSpurRemoval				= new ModelValueBoolean("Spur removal", isSpurFilter);
	private ModelValueBoolean						parameterWaterfallVisible			= new ModelValueBoolean("Waterfall visible", true);

	private ModelValueBoolean						parameterChartVisible			= new ModelValueBoolean("Chart visible", true);

	private ModelValueBoolean						parameterIsCrossEnabled			= new ModelValueBoolean("isCrossEnabled", isAim);

	private ModelValue<String>						parameterSerif		= new ModelValue<>("Serif Variant", serifVariant);

	private ModelValueInt							parameterCounterForSerif		= new ModelValueInt("counter for serif", counterForSignal);

	private ModelValueInt							parameterCounterLoseSignal		= new ModelValueInt("counter for lose signal", MISS_THRESHOLD);

	private ModelValueInt							parameterSubRange		= new ModelValueInt("subrange parameter", subRange);

	private PersistentDisplay						persistentDisplay					= new PersistentDisplay();
	private float									spectrumInitValue					= -150;
	private SpurFilter								spurFilter;
	private Thread									threadHackrfSweep;
	private ArrayBlockingQueue<Integer>				threadLaunchCommands				= new ArrayBlockingQueue<>(1);
	private Thread									threadLauncher;
	private Thread									threadProcessing;
	private TextTitle								titleFreqBand						= new TextTitle("",
			new Font("Dialog", Font.PLAIN, 11));
	private RuntimePerformanceWatch					perfWatch							= new RuntimePerformanceWatch();
	private JFrame									uiFrame;
	private ValueMarker								waterfallPaletteEndMarker;
	private ValueMarker								waterfallPaletteStartMarker;
	private WaterfallPlot							waterfallPlot;
	private JLabel labelMessages;
	private ModelValueInt							dbLine		= new ModelValueInt("Spectrum dbline", minLineForSerif);
	private HackRFSweepSettingsUI sett;
	private SocketIOGadalka socketIOGadalka;
	private NumberSource numberSource;
	private WebSocketArraySpectrum webSocketArraySpectrum;
	private ArrayList<JLabel> valueLabels = new ArrayList<>();
	private JPanel sidePanel = new JPanel();
	public AzimuthPanel azimuthPanel;
	private Timer timerAzimuth;
	private Rectangle2D currentRectangle;
	private final List<ColoredRectangle> selectionRectangles = new ArrayList<>();
	private Double startX, endX;
	private List<RangeForSerif> rangeForSerifs = new ArrayList<>();
	private Color currentColor = Color.BLUE;
	private JSplitPane splitPane;
	private boolean checkForVisible = false;
	private JPanel waterfallContainer;
	private List<SubRangeMinimalLineSerif> listMinLine;

	private SignalStreamProcessor signalStreamProcessor;
	private  ConfigSerif configSerif;

	public HackRFSweepSpectrumAnalyzer() {
		printInit(0);


		if (captureGIF) {
//			parameterFrequency.setValue(new FrequencyRange(700, 2700));
			parameterFrequency.setValue(new FrequencyRange(666, 777));
			parameterGainTotal.setValue(60);
			parameterSpurRemoval.setValue(true);
			parameterPersistentDisplay.setValue(true);
			parameterFFTBinHz.setValue(500000);
			parameterFrequencyAllocationTable.setValue(new FrequencyAllocations().getTable().values().stream().findFirst().get());
		}


		recalculateGains(parameterGainTotal.getValue());
		rangeForSerifs = ranges1;
		System.out.println(123);

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		//		UIManager.getLookAndFeelDefaults().put("TabbedPane.borderHightlightColor", Color.black);
		//		UIManager.getLookAndFeelDefaults().put("TabbedPane.background", Color.black);
		//		UIManager.getLookAndFeelDefaults().put("TabbedPane.contentAreaColor", Color.black);
		//		UIManager.getLookAndFeelDefaults().put("TabbedPane.darkShadow", Color.black);
		//		UIManager.getLookAndFeelDefaults().put("TabbedPane.focus", Color.black);
		//		UIManager.getLookAndFeelDefaults().put("TabbedPane.highlight", Color.black);
		//		UIManager.getLookAndFeelDefaults().put("TabbedPane.light", Color.black);
		//		UIManager.getLookAndFeelDefaults().put("TabbedPane.selected", Color.black);
		//		UIManager.getLookAndFeelDefaults().put("TabbedPane.selectedForeground", Color.black);
		//		UIManager.getLookAndFeelDefaults().put("TabbedPane.selectHighlight", Color.black);
		//		UIManager.getLookAndFeelDefaults().put("TabbedPane.shadow", Color.black);
		//		UIManager.getLookAndFeelDefaults().put("TabbedPane.tabAreaBackground", Color.black);

		Insets insets = new Insets(1, 1, 1, 1);
		UIManager.getLookAndFeelDefaults().put("TabbedPane.contentBorderInsets", insets);
		UIManager.getLookAndFeelDefaults().put("TabbedPane.selectedTabPadInsets", insets);
		UIManager.getLookAndFeelDefaults().put("TabbedPane.tabAreaInsets", insets);
		//		UIManager.getLookAndFeelDefaults().put("", insets);
		//		UIManager.getLookAndFeelDefaults().put("", insets);

		//		UIManager.getLookAndFeelDefaults().values().forEach((p) -> {
		//			System.out.println(p.toString());
		//		});

		setupChart();

		setupChartMouseMarkers();

		numberSource = new NumberSource();


        URI uri = URI.create("http://"+ipadress+":"+port);

		webSocketArraySpectrum = new WebSocketArraySpectrum();
		socketIOGadalka = new SocketIOGadalka(hackrfID, uri, uid, this, webSocketArraySpectrum);


        //SocketDataListener listener = new SocketDataListener(socketOkko);
		azimuthPanel = new AzimuthPanel(100);
		//azimuthPanel.setPreferredSize(new Dimension(30, 0));  // Фиксируем ширину 20, высота будет динамической
		//azimuthPanel.setBackground(Color.RED);



		waterfallPlot = new WaterfallPlot(chartPanel, 300, this, socketIOGadalka);
		waterfallPlot.setSpectrumPaletteStart(startWaterfallPalette.doubleValue());
		waterfallPlot.setSpectrumPaletteSize(sizeWaterfallPalette.doubleValue());
		waterfallPaletteStartMarker = new ValueMarker(waterfallPlot.getSpectrumPaletteStart(), colors.palette2,
				new BasicStroke(1f));
		waterfallPaletteEndMarker = new ValueMarker(
				waterfallPlot.getSpectrumPaletteStart() + waterfallPlot.getSpectrumPaletteSize(), colors.palette2,
				new BasicStroke(1f));
		//		chart.getXYPlot().addRangeMarker(waterfallPaletteStartMarker);
		//		chart.getXYPlot().addRangeMarker(waterfallPaletteEndMarker);

		printInit(2);


        HackRFSweepSettingsUI settingsPanel = new HackRFSweepSettingsUI(
				this,
				socketIOGadalka, this

		);
		sett = settingsPanel;

		try {

			File dirFileSerif = new File("/home/user/gadalkaLogs/analizatorLogs/seriflogs");
			if (!dirFileSerif.exists()) {
				System.out.println("создание папки seriflogs");
				dirFileSerif.mkdirs();
			}
			File dirFileSignal = new File("/home/user/gadalkaLogs/analizatorLogs/signallogs");
			if (!dirFileSignal.exists()) {
				System.out.println("создание папки signallogs");
				dirFileSignal.mkdirs();
			}
			File fileLogsSerif = new File("/home/user/gadalkaLogs/analizatorLogs/seriflogs/"+hackrfID +".txt");
			if (fileLogsSerif.createNewFile()) {
				System.out.println("Файл создан");
			} else {
				System.out.println("Файл уже существует");
				java.time.LocalDate currentDate = java.time.LocalDate.now();
				File fileLogsSerifArchive = new File("/home/user/gadalkaLogs/analizatorLogs/seriflogs/" + hackrfID + "_archive_" +currentDate+ ".txt");
				if (fileLogsSerifArchive.exists()) {
					appendFile(fileLogsSerif,fileLogsSerifArchive);
					try (FileWriter writer = new FileWriter(fileLogsSerif, false)) { // false -> перезапись файла (очистка)
						System.out.println("Файл успешно очищен.");
					} catch (IOException e) {
						System.out.println("Ошибка при очистке файла.");
						e.printStackTrace();
					}
				}
				else {
					//throw new RuntimeException("Some shit");
					/*Files.move(Path.of("/home/user/gadalkaLogs/analizatorLogs/seriflogs/" + hackrfID + ".txt"),
							Path.of("/home/user/gadalkaLogs/analizatorLogs/seriflogs/" + hackrfID + "_archive_" +currentDate+ ".txt"),
							StandardCopyOption.REPLACE_EXISTING);
*/
				}
			}
			File fileLogsSignal = new File("/home/user/gadalkaLogs/analizatorLogs/signallogs/"+hackrfID +".txt");
			if (fileLogsSignal.createNewFile()) {
				System.out.println("Файл логов для сигналов создан");
			} else {
				System.out.println("Файл логов для сигналов уже существует");
				java.time.LocalDate currentDate = java.time.LocalDate.now();
				File fileLogsSignalArchive = new File("/home/user/gadalkaLogs/analizatorLogs/signallogs/" + hackrfID + "_archive_" +currentDate+ ".txt");
				if (fileLogsSignalArchive.exists()) {
					appendFile(fileLogsSignal,fileLogsSignalArchive);
					try (FileWriter writer = new FileWriter(fileLogsSignal, false)) { // false -> перезапись файла (очистка)
						System.out.println("Файл логов для сигналов успешно очищен.");
					} catch (IOException e) {
						System.out.println("Ошибка при очистке файла логов для сигналов.");
						e.printStackTrace();
					}
				}
				else {
					/*Files.move(Path.of("/home/user/gadalkaLogs/analizatorLogs/signallogs/" + hackrfID + ".txt"),
							Path.of("/home/user/gadalkaLogs/analizatorLogs/signallogs/" + hackrfID + "_archive_" +currentDate+ ".txt"),
							StandardCopyOption.REPLACE_EXISTING);*/
					//throw new RuntimeException("Some shit");
				}
			}
			String serifDir = "/gadalkaLogs/analizatorLogs/seriflogs";
			String signalDir = "/gadalkaLogs/analizatorLogs/signallogs";
			deleteOldLogs(serifDir);
			deleteOldLogs(signalDir);

		} catch (IOException e) {
			System.out.println("Ошибка при создании файла");
			e.printStackTrace();
		}

		configSerif = new ConfigSerif(
				parameterFrequency.getValue().getStartMHz(),
				parameterFrequency.getValue().getEndMHz(),
				parameterFFTBinHz.getValue(),
				parameterSubRange.getValue(),
				listMinLine,
				parameterCounterForSerif.getValue(),
				parameterCounterLoseSignal.getValue(),
				rangeForSerifs);


		ConditionalDataListener2 listener2 = new ConditionalDataListener2(numberSource);
		listener2.getDataObservable()
				.subscribe(data -> {
					float roundedValue = Math.round(data.getDbm() * 10) / 10.0f; // -30.3
					sett.serifMessage2(getFreq().getStartMHz(), getFreq().getEndMHz(), hackrfID, data.getAzimuth(), roundedValue);
				});
		try {
			signalStreamProcessor = new SignalStreamProcessor(configSerif);
			signalStreamProcessor.getSignalStream().subscribeOn(Schedulers.io()) // Здесь подписка будет происходить на отдельном потоке
					.observeOn(Schedulers.computation()) // Результаты будут обрабатываться в вычислительном потоке
			 .subscribe(
					results -> {
						for (SubRangeResult result : results) {
							// Работа с каждым результатом
							System.out.println(result);
							float roundedValue = Math.round(result.getMaxDbm() * 10) / 10.0f; // -30.3
							System.out.println(roundedValue);
							sett.serifMessage2(result.getStartFreq(),result.getEndFreq(),hackrfID,result.getCurrentAzimuth(),roundedValue);
						}
					}
			);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}


		/*udpListener.getDataObservable()
				.subscribe(data ->{
					try {
						socketIOGadalka.webSocket.sendBinary(data, true).thenRun(() -> {
							System.out.println("Бинарное сообщение отправлено: ");
						}).exceptionally(e -> {
							System.err.println("Ошибка при отправке бинарного сообщения: " + e.getMessage());
							if (socketIOGadalka.webSocket.isOutputClosed()) {
								socketIOGadalka.isSend = false;
								System.out.println("websocket закрыт");
							}
							return null;
						});

					} catch (Exception e) {
						System.out.println("ошибка в листенере udp передачи " + e.getMessage());
					}
				});*/

		printInit(3);

		//sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
		//sidePanel.setPreferredSize(new Dimension(30, 0));  // Фиксируем ширину 20, высота будет динамической
		//sidePanel.setBackground(Color.GRAY);

        waterfallContainer = new JPanel(new BorderLayout());
		waterfallContainer.add(waterfallPlot, BorderLayout.CENTER);  // Водопад занимает центральную область
		waterfallContainer.add(azimuthPanel, BorderLayout.EAST);
		waterfallContainer.setPreferredSize(null);
		waterfallContainer.setMinimumSize(new Dimension(0, 0));
		//waterfallContainer.add(sidePanel, BorderLayout.EAST);
		splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartPanel, waterfallContainer);
		splitPane.setResizeWeight(0.4);
		splitPane.setBorder(null);

		labelMessages = new JLabel("dsadasd");
		labelMessages.setForeground(Color.white);
		labelMessages.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		parameterDebugDisplay.addListener((debug) -> {
			labelMessages.setVisible(debug);
		});
		waterfallPlot.setIsCrossEnabled(parameterIsCrossEnabled.getValue());
		parameterIsCrossEnabled.addListener(isCross ->{
			waterfallPlot.setIsCrossEnabled(isCross);
		});
		if (!Objects.equals(parameterSerif.getValue(), "Мульти максимум"))
			sett.visibleLabel();
		parameterDebugDisplay.callObservers();
		
		JPanel splitPanePanel	= new JPanel(new BorderLayout());
		splitPanePanel.setBackground(Color.black);
		splitPanePanel.add(splitPane, BorderLayout.CENTER);
		splitPanePanel.add(labelMessages, BorderLayout.SOUTH);

		uiFrame = new JFrame(freqStart + " - " + freqEnd);
		uiFrame.setUndecorated(captureGIF);
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int width = screenSize.width/2;
		int height = screenSize.height/2;
		if (numberApp == 1) uiFrame.setLocation(0,0);
		else if (numberApp ==2) uiFrame.setLocation(0,height);
		else if (numberApp ==3) uiFrame.setLocation(width,0);
		else if (numberApp ==4) uiFrame.setLocation(width,height);
		else uiFrame.setLocation(0,0);
		//uiFrame.setExtendedState(uiFrame.getExtendedState() | Frame.MAXIMIZED_BOTH);
		uiFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		uiFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				super.windowClosing(e);
				socketIOGadalka.closeConnect();
				if (hackrfID == "123456789") hackrfID = "";
				saveConfig();

			}
		});
		uiFrame.setLayout(new BorderLayout());

		uiFrame.add(splitPanePanel, BorderLayout.CENTER);
		if (numberApp ==3 || numberApp == 4)
			uiFrame.setMinimumSize(new Dimension(width, height));
		else
			uiFrame.setMinimumSize(new Dimension(width, height));
		JScrollPane scrollPane = new JScrollPane(settingsPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		uiFrame.add(scrollPane, BorderLayout.EAST);
		try {
			uiFrame.setIconImage(new ImageIcon("program.png").getImage());
		} catch (Exception e) {
			//			e.printStackTrace();
		}


		
		printInit(4);
		setupFrequencyAllocationTable();
		printInit(5);
		
		uiFrame.pack();
		uiFrame.setTitle(freqStart + " - " + freqEnd);
		uiFrame.setVisible(true);
		uiFrame.setFocusable(false);
		uiFrame.setFocusTraversalPolicy(new DefaultFocusTraversalPolicy() {
			@Override
			public boolean accept(Component c) {
				return false; // Запрещаем всем компонентам получать фокус
			}
		});
		if (numberApp ==3 || numberApp == 4)
			uiFrame.setSize(width,height-70);
		else
			uiFrame.setSize(width-70,height-70);

		printInit(6);

		startLauncherThread();
		restartHackrfSweep();

		/**
		 * register parameter observers
		 */
		setupParameterObservers();

		//shutdown on exit
		Runtime.getRuntime().addShutdownHook(new Thread(this::stopHackrfSweep));

		if (captureGIF) {
			try {
				gifCap = new ScreenCapture(uiFrame, 35 * 1, 10, 5, 760, 660, new File("screenshot.gif"));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		System.out.println("konec hackrf fun");
	}

	@Override
	public ModelValueBoolean getAntennaPowerEnable() {
		return parameterAntennaPolarization;
	}

	@Override
	public ModelValueInt getFFTBinHz() {
		return parameterFFTBinHz;
	}

	@Override
	public ModelValue<FrequencyRange> getFrequency() {
		return parameterFrequency;
	}

	@Override
	public ModelValue<FrequencyAllocationTable> getFrequencyAllocationTable() {
		return parameterFrequencyAllocationTable;
	}

	@Override
	public ModelValueInt getGain() {
		return parameterGainTotal;
	}

	@Override
	public ModelValueInt getGainLNA() {
		return parameterGainLNA;
	}

	@Override
	public ModelValueInt getGainVGA() {
		return parameterGainVGA;
	}

	@Override
	public ModelValueBoolean getAntennaLNA() {
		return parameterAntennaLNA;
	}
	
	@Override
	public ModelValueInt getPeakFallRate() {
		return parameterPeakFallRateSecs;
	}

	@Override
	public ModelValueInt getSamples() {
		return parameterSamples;
	}

	@Override
	public ModelValue<BigDecimal> getSpectrumLineThickness() {
		return parameterSpectrumLineThickness;
	}
	
	@Override
	public ModelValueInt getPersistentDisplayDecayRate() {
		return parameterPersistentDisplayPersTime;
	}

	@Override
	public ModelValueInt getSpectrumPaletteSize() {
		return parameterSpectrumPaletteSize;
	}

	@Override
	public ModelValueInt getSpectrumPaletteStart() {
		return parameterSpectrumPaletteStart;
	}

	@Override
	public ModelValueBoolean isCapturingPaused() {
		return parameterIsCapturingPaused;
	}

	@Override
	public ModelValueBoolean isChartsPeaksVisible() {
		return parameterShowPeaks;
	}
	
	@Override
	public ModelValueBoolean isDebugDisplay() {
		return parameterDebugDisplay;
	}

	@Override
	public ModelValueBoolean isFilterSpectrum() {
		return parameterFilterSpectrum;
	}

	@Override
	public ModelValueBoolean isPersistentDisplayVisible() {
		return parameterPersistentDisplay;
	}

	@Override
	public ModelValueBoolean isSpurRemoval() {
		return this.parameterSpurRemoval;
	}

	@Override
	public ModelValueInt linePing() {
		return dbLine;
	}

	@Override
	public ModelValueBoolean isWaterfallVisible() {
		return parameterWaterfallVisible;
	}

	@Override
	public ModelValueBoolean isChartVisible() {
		return parameterChartVisible;
	}

	@Override
	public ModelValue<String> getSerifVariant() {
		return parameterSerif;
	}

	@Override
	public ModelValueInt counterForSerif() {
		return parameterCounterForSerif;
	}

	@Override
	public ModelValueInt counterForLoseSignal() {
		return parameterCounterLoseSignal;
	}

	@Override
	public ModelValueBoolean isCrossEnabled() {
        return parameterIsCrossEnabled;
    }

	@Override
	public ModelValueInt getSubRange() {
		return parameterSubRange;
	}

	@Override
	public void azimuth(Integer azimuth) {
		/*JLabel label = new JLabel(String.valueOf(azimuth));
		if (valueLabels.size() > 14) {
			sidePanel.remove(valueLabels.getLast());
			valueLabels.removeLast();
		}
		valueLabels.addFirst(label);
		sidePanel.add(valueLabels.getFirst(), 0);
		sidePanel.revalidate();
		sidePanel.repaint();*/
		//newAzimuths.add(azimuth);
		numberSource.setCurrentAzimuth(azimuth);
		try {
			azimuthPanel.addAzimuthValue(azimuth);
			webSocketArraySpectrum.setAzimuth(azimuth);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

	}

	@Override
	public Boolean setSettings(int startFreq,
							   int endFreq,
							   int gainLNA,
							   int gainVGA,
							   int samples,
							   int fftBins,
							   int startPalette,
							   int sizePalette,
							   boolean antennaLNA1,
							   int counterSignal,
							   int loseCounter,
							   int subRange,
							   boolean antennaPolarization) {
		try{
			parameterFrequency.setValue(new FrequencyRange(startFreq, endFreq));
			parameterGainLNA.setValue(gainLNA);
			parameterGainVGA.setValue(gainVGA);
			parameterSamples.setValue(samples);
			parameterFFTBinHz.setValue(fftBins);
			parameterAntennaLNA.setValue(antennaLNA1);
			parameterSpectrumPaletteStart.setValue(startPalette);
			parameterSpectrumPaletteSize.setValue(sizePalette);
			parameterCounterForSerif.setValue(counterSignal);
			parameterCounterLoseSignal.setValue(loseCounter);
			parameterSubRange.setValue(subRange);
			parameterAntennaPolarization.setValue(antennaPolarization);

			saveConfig();

			FrequencyRange range = getFreq();

			SpectrumConfig config = new SpectrumConfig(
					range.getStartMHz(),
					range.getEndMHz(),
					parameterAntennaLNA.getValue(),
					getGainLNA().getValue(),
					getGainVGA().getValue(),
					parameterSpectrumPaletteStart.getValue(),
					parameterSpectrumPaletteSize.getValue(),
					getSamples().getValue(),
					getFFTBinHz().getValue(),
					parameterCounterForSerif.getValue(),
					parameterCounterLoseSignal.getValue(),
					parameterSubRange.getValue(),
					parameterAntennaPolarization.getValue()
			);

			socketIOGadalka.setSpectrumConfig(config);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public boolean setTypeSerif( String serifVariant) {
		parameterSerif.setValue(serifVariant);
		if (Objects.equals(parameterSerif.getValue(), serifVariant)) {
			numberSource.setCurrentSerifVariant(serifVariant);
			saveConfig();
			return true;
		}
		else return false;
	}

	@Override
	public boolean addSubCreate(String type, double startFreq, double endFreq) {
		RangeForSerif subCreate = new RangeForSerif(startFreq,endFreq,type);
		rangeForSerifs.add(subCreate);
		boolean status = rangeForSerifs.contains(subCreate);
		return status;
	}

	@Override
	public boolean deleteSub(double freq) {
        rangeForSerifs.removeIf(range -> freq > range.getStartX() && freq < range.getEndX());

        return rangeForSerifs.stream()
                .noneMatch(range -> freq > range.getStartX() && freq < range.getEndX());
	}

	@Override
	public boolean deleteAllSub() {
		rangeForSerifs.clear();
        return true;
	}

	@Override
	public void changeAntennaPolarization(boolean antennaPolarization) {
		parameterAntennaPolarization.setValue(antennaPolarization);
	}

	@Override
	public boolean changeSerifLine(List<SubRangeMinimalLineSerif> subRangeMinimalLineSerifList) {
		listMinLine = subRangeMinimalLineSerifList;
		if (!Objects.equals(parameterSerif.getValue(), "Мульти максимум"))
			dbLine.setValue(subRangeMinimalLineSerifList.getFirst().getDbLine());
		return true;
	}

	@Override
	public boolean setAllSettings(int startFreq, int endFreq, int gainLNA, int gainVGA, int samples, int fftBins, int startPalette, int sizePalette, boolean antennaLNA, int counterSignal, int loseCounter, int subRange, boolean antennaPolarization, String serifVariant, List<RangeForSerif> rangeForSerifList, List<SubRangeMinimalLineSerif> subRangeMinimalLineSerifList) {
		try {
			parameterFrequency.setValue(new FrequencyRange(startFreq, endFreq));
			parameterGainLNA.setValue(gainLNA);
			parameterGainVGA.setValue(gainVGA);
			parameterSamples.setValue(samples);
			parameterFFTBinHz.setValue(fftBins);
			parameterAntennaLNA.setValue(antennaLNA);
			parameterSpectrumPaletteStart.setValue(startPalette);
			parameterSpectrumPaletteSize.setValue(sizePalette);
			parameterCounterForSerif.setValue(counterSignal);
			parameterCounterLoseSignal.setValue(loseCounter);
			parameterSubRange.setValue(subRange);
			parameterAntennaPolarization.setValue(antennaPolarization);

			parameterSerif.setValue(serifVariant);
			if (Objects.equals(parameterSerif.getValue(), serifVariant)) {
				numberSource.setCurrentSerifVariant(serifVariant);
			}

			if (!rangeForSerifList.isEmpty())
				rangeForSerifs = rangeForSerifList;

			if (!subRangeMinimalLineSerifList.isEmpty()) {
				listMinLine = subRangeMinimalLineSerifList;
				if (!Objects.equals(parameterSerif.getValue(), "Мульти максимум"))
					dbLine.setValue(subRangeMinimalLineSerifList.getFirst().getDbLine());
			}

			saveConfig();
			return true;

		} catch (Exception e) {
			System.out.println(e.getMessage());
			return false;
		}
	}


	@Override
	public void newSpectrumData(boolean fullSweepDone, double[] frequencyStart, float fftBinWidthHz,
								float[] signalPowerdBm) {
		fireHardwareStateChanged(true);
		//System.out.println("---------------------------------------------------------------------|"+hwProcessingQueue.size());
		if (hwProcessingQueue.size() > 100) {
			System.out.println("---------------------------------------------------------------------|"+hwProcessingQueue.size());
		}
		if (!hwProcessingQueue.offer(new FFTBins(fullSweepDone, frequencyStart, fftBinWidthHz, signalPowerdBm))) {
			System.out.println("queue full");
			// завершить и снова начать чтения из файла

			System.out.println(hwProcessingQueue.size());
			parameterFrequency.setValue(new FrequencyRange(getFreq().getStartMHz(), getFreq().getEndMHz()+1));
			parameterFrequency.setValue(new FrequencyRange(getFreq().getStartMHz(), getFreq().getEndMHz()-1));
			hwProcessingQueue.clear();
			//hwProcessingQueue.remove();

			dropped++;
		}
	}

	@Override
	public void errorData(int numberError) {
		switch (numberError) {
			case -2:{
				System.out.println("HACKRF_ERROR_INVALID_PARAM");
			}
			case  -5: {
				int result = JOptionPane.showConfirmDialog(null, "Hackrf не подключен, проверьте подключение и корректность id \n" +
						"(нажимая No вы закроете программу)", "Перезапустить?", JOptionPane.YES_NO_OPTION);
				if (result == JOptionPane.YES_OPTION) {
					restartHackrfSweep();
				} else {
					Runtime.getRuntime().halt(0);
				}
			}
			case -6:{
				System.out.println("HACKRF_ERROR_BUSY");
			}
			case -11: {
				System.out.println("HACKRF_ERROR_NO_MEM");
			}
			case -1000:{
				System.out.println("HACKRF_ERROR_LIBUSB");
			}
			case -1001:{
				System.out.println("HACKRF_ERROR_THREAD");
			}
			case -1002:{
				System.out.println("HACKRF_ERROR_STREAMING_THREAD_ERR");
			}
			case -1003:{
				System.out.println("HACKRF_ERROR_STREAMING_STOPPED");
			}
			case -1004:{
				System.out.println("HACKRF_ERROR_STREAMING_EXIT_CALLED");
			}
			case -1005:{
				System.out.println("HACKRF_ERROR_USB_API_VERSION");
			}
			case -2000:{
				System.out.println("HACKRF_ERROR_NOT_LAST_DEVICE");
			}
			case -9999:{
				System.out.println("HACKRF_ERROR_OTHER");
			}
		}
	}

/*	@Override
	public void newSpectrumData(boolean fullSweepDone, double[] frequencyStart, float fftBinWidthHz,
			float[] signalPowerdBm) {
		//		System.out.println(frequencyStart+" "+fftBinWidthHz+" "+signalPowerdBm);
		fireHardwareStateChanged(true);
		if (hwProcessingQueue.size() > 100) {
			System.out.println(hwProcessingQueue.size());
		}
		if (!hwProcessingQueue.offer(new FFTBins(fullSweepDone, frequencyStart, fftBinWidthHz, signalPowerdBm))) {
			System.out.println("queue full");
			System.out.println(hwProcessingQueue.size());
			dropped++;
		}
	}*/

	@Override
	public void registerListener(HackRFEventListener listener) {
		hRFlisteners.add(listener);
	}

	@Override
	public void removeListener(HackRFEventListener listener) {
		hRFlisteners.remove(listener);
	}

	private void fireCapturingStateChanged() {
		SwingUtilities.invokeLater(() -> {
			synchronized (hRFlisteners) {
				for (HackRFEventListener hackRFEventListener : hRFlisteners) {
					try {
						hackRFEventListener.captureStateChanged(!parameterIsCapturingPaused.getValue());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		});
	}

	private void fireHardwareStateChanged(boolean sendingData) {
		if (this.flagIsHWSendingData != sendingData) {
			this.flagIsHWSendingData = sendingData;
			SwingUtilities.invokeLater(() -> {
				synchronized (hRFlisteners) {
					for (HackRFEventListener hackRFEventListener : hRFlisteners) {
						try {
							hackRFEventListener.hardwareStatusChanged(sendingData);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			});
		}
	}

	private FrequencyRange getFreq() {
		return parameterFrequency.getValue();
	}

	private int diap =1;

	public synchronized void setDiap(int start,int end)
	{
		this.diap = end - start;
	}

	public synchronized int getDiap()
	{
		return this.diap;
	}

	private void printInit(int initNumber) {
		//		System.out.println("Startup "+(initNumber++)+" in " + (System.currentTimeMillis() - initTime) + "ms");
	}

	private void processingThread() {
		long counter = 0;
		long frameCounterChart = 0;

		//mainWhile:
		//while(true)
		{
			FFTBins bin1 = null;
			try {
				bin1 = hwProcessingQueue.take();
			} catch (InterruptedException e1) {
				return;
			}
			float binHz = bin1.fftBinWidthHz;

			/**
			 * prevents from spectrum chart from using too much CPU
			 */
			int limitChartRefreshFPS		= 30;
			int limitPersistentRefreshEveryChartFrame	= 2;
			
			//			PowerCalibration calibration	 = new PowerCalibration(-45, -12.5, 40); 

			datasetSpectrum = new DatasetSpectrumPeak(binHz, getFreq().getStartMHz(), getFreq().getEndMHz(),
					spectrumInitValue, 15, parameterPeakFallRateSecs.getValue() * 1000);
			chart.getXYPlot().getDomainAxis().setRange(getFreq().getStartMHz(), getFreq().getEndMHz());
			setDiap(datasetSpectrum.getFreqStartMHz(), datasetSpectrum.getFreqStopMHz());

			XYSeries spectrumPeaksEmpty	= new XYSeries("peaks");
			
			float maxPeakJitterdB = 6;
			float peakThresholdAboveNoise = 4;
			int maxPeakBins = 4;
			int validIterations = 25;
			spurFilter = new SpurFilter(maxPeakJitterdB, peakThresholdAboveNoise, maxPeakBins, validIterations,
					datasetSpectrum);

			long lastChartUpdated = System.currentTimeMillis();
			long lastScanStartTime = System.currentTimeMillis();
			double lastFreq = 0;

			while (true) {
				try {
					counter++;
					FFTBins bins = hwProcessingQueue.take();
					if (parameterIsCapturingPaused.getValue())
						continue;
					boolean triggerChartRefresh = bins.fullSweepDone;
					//continue;
				
					if (bins.freqStart != null && bins.sigPowdBm != null) {
						//						PowerCalibration.correctPower(calibration, parameterGaindB, bins);
						datasetSpectrum.addNewData(bins);
					}

					if ((triggerChartRefresh/* || timeDiff > 1000 */)) {
						//						System.out.println("ctr "+counter+" dropped "+dropped);
						/**
						 * filter first
						 */
						if (parameterSpurRemoval.getValue()) {
							long start	= System.nanoTime();
							spurFilter.filterDataset();
							synchronized (perfWatch) {
								perfWatch.spurFilter.addDrawingTime(System.nanoTime()-start);
							}
						}
						/**
						 * after filtering, calculate peak spectrum
						 */
						if (parameterShowPeaks.getValue()) {
							datasetSpectrum.refreshPeakSpectrum();
							waterfallPlot.setStatusMessage(String.format("Total Spectrum Peak Power %.1fdBm",
									datasetSpectrum.calculateSpectrumPeakPower()), 0);
						}

						/**
						 * Update performance counters
						 */
						if (System.currentTimeMillis() - perfWatch.lastStatisticsRefreshed > 1000) {
							synchronized (perfWatch) {
//								waterfallPlot.setStatusMessage(perfWatch.generateStatistics(), 1);
								perfWatch.waterfallDraw.nanosSum	= waterfallPlot.getDrawTimeSumAndReset();
								perfWatch.waterfallDraw.count	= waterfallPlot.getDrawingCounterAndReset();
								String stats	= perfWatch.generateStatistics();
								SwingUtilities.invokeLater(() -> {
									labelMessages.setText(stats);
								});
								perfWatch.reset();
							}
						}

						boolean flagChartRedraw	= false;
						/**
						 * Update chart in the swing thread
						 */
						if (System.currentTimeMillis() - lastChartUpdated > 1000/limitChartRefreshFPS) {
							flagChartRedraw	= true;
							frameCounterChart++;
							lastChartUpdated = System.currentTimeMillis();
						}


						XYSeries spectrumSeries;
						XYSeries spectrumPeaks;

						if (true) {
							spectrumSeries = datasetSpectrum.createSpectrumDataset("spectrum");

							if (parameterShowPeaks.getValue()) {
								spectrumPeaks = datasetSpectrum.createPeaksDataset("peaks");
							} else {
								spectrumPeaks = spectrumPeaksEmpty;
							}
						} else {
							spectrumSeries = new XYSeries("spectrum", false, true);
							spectrumSeries.setNotify(false);
							datasetSpectrum.fillToXYSeries(spectrumSeries);
							spectrumSeries.setNotify(true);

							spectrumPeaks =
									//									new XYSeries("peaks");
									new XYSeries("peaks", false, true);
							if (parameterShowPeaks.getValue()) {
								spectrumPeaks.setNotify(false);
								datasetSpectrum.fillPeaksToXYSeries(spectrumPeaks);
								spectrumPeaks.setNotify(false);
							}
						}

						if (parameterPersistentDisplay.getValue()) {
							long start	= System.nanoTime();
							boolean redraw	= false;
							if (flagChartRedraw && frameCounterChart % limitPersistentRefreshEveryChartFrame == 0)
								redraw	= true;
							
							//persistentDisplay.drawSpectrumFloat
							persistentDisplay.drawSpectrum2
							(datasetSpectrum,
									(float) chart.getXYPlot().getRangeAxis().getRange().getLowerBound(),
									(float) chart.getXYPlot().getRangeAxis().getRange().getUpperBound(), redraw);
							synchronized (perfWatch) {
								perfWatch.persisentDisplay.addDrawingTime(System.nanoTime()-start);	
							}
						}

						/**
						 * do not render it in swing thread because it might
						 * miss data
						 */
						if (parameterWaterfallVisible.getValue()) {
							long start	= System.nanoTime();
							try {
								waterfallPlot.addNewData(datasetSpectrum);
							} catch (Exception e) {
								System.out.println(e.getMessage());
							}
							synchronized (perfWatch) {
								perfWatch.waterfallUpdate.addDrawingTime(System.nanoTime()-start);	
							}
						}

						if (parameterChartVisible.getValue()) {
							if (!splitPane.getLeftComponent().isVisible()) {
								checkForVisible = true;
								chartPanel.setVisible(true);
								splitPane.revalidate(); // Пересчитываем размеры в JSplitPane
								splitPane.repaint();    // Перерисовываем интерфейс
							}
						} else {
							chartPanel.setVisible(false);
							//sidePanel.revalidate(); // Обновляем компоновку
							//sidePanel.repaint();    // Перерисовываем UI

						}
						
						if (flagChartRedraw) {
							if (parameterWaterfallVisible.getValue()) {
								waterfallPlot.repaint();
							}
							SwingUtilities.invokeLater(() -> {

								chart.setNotify(false);
								chartDataset.removeAllSeries();

								//spectrumLine.add(spectrumSeries.getMinX(),-50.0);
								//spectrumLine.add(spectrumSeries.getMaxX(),-50.0);
								if (!Objects.equals(parameterSerif.getValue(), "Мульти максимум")) {
									float[] xValues = new float[2];
									float[] yValues = new float[2];
									xValues[0] = (float) getFreq().getStartMHz();
									xValues[1] = (float) getFreq().getEndMHz();
									yValues[0] = dbLine.getValue().floatValue();
									yValues[1] = dbLine.getValue().floatValue();
									XYSeriesImmutable spectrumLine = new XYSeriesImmutable("line", xValues, yValues);
									chartDataset.addSeries(spectrumLine);
								} else {
									int startMHz = getFreq().getStartMHz();
									int endMhz = getFreq().getEndMHz();
									int finalRange = (endMhz - startMHz) / parameterSubRange.getValue();
									if (listMinLine == null || listMinLine.size() != finalRange){
										listMinLine = new ArrayList<>(finalRange);
										int i = 0;
										for (int start = startMHz; start< endMhz; start = start + parameterSubRange.getValue()) {
											SubRangeMinimalLineSerif subRangeMinimalLineSerif = new SubRangeMinimalLineSerif(
													start,
													(start + parameterSubRange.getValue()),
													dbLine.getValue());
											listMinLine.add(subRangeMinimalLineSerif);
											float[] xValues = new float[2];
											float[] yValues = new float[2];
											xValues[0] = start;
											xValues[1] = start + parameterSubRange.getValue();
											yValues[0] = listMinLine.get(i).getDbLine();
											yValues[1] = listMinLine.get(i).getDbLine();
											XYSeriesImmutable spectrumLine = new XYSeriesImmutable(start, xValues, yValues);
											chartDataset.addSeries(spectrumLine);
											i++;
										}
									} else {
										for (int i = 0; i< listMinLine.size(); i++){
											float[] xValues = new float[2];
											float[] yValues = new float[2];
											xValues[0] = listMinLine.get(i).getStartFreq();
											xValues[1] = listMinLine.get(i).getEndFreq();
											yValues[0] = listMinLine.get(i).getDbLine();
											yValues[1] = listMinLine.get(i).getDbLine();
											XYSeriesImmutable spectrumLine = new XYSeriesImmutable(listMinLine.get(i).getStartFreq(), xValues, yValues);
											chartDataset.addSeries(spectrumLine);
										}
									}

								}
								chartDataset.addSeries(spectrumPeaks);
								chartDataset.addSeries(spectrumSeries);
								float[]  arrays1 = datasetSpectrum.getSpectrumArray();

								webSocketArraySpectrum.setNumber(arrays1);
								boolean checkForRange = false;
								int rangeIndex = 0;
								boolean checkForSerif = false;
								//numberSource.setCurrentSerifVariant(parameterSerif.getValue());
								if (Objects.equals(parameterSerif.getValue(), "Середина")) {
									float maxDBm = findMaxWithoutIgnore(arrays1,rangeForSerifs,dbLine,
											getFreq().getStartMHz(),getFFTBinHz().getValue());
									if (maxDBm != Float.NEGATIVE_INFINITY) {
										counterForEmit++;
										numberSource.records.add(new AzimuthDbm(numberSource.getCurrentAzimuth(), maxDBm));
										System.out.println("counter = " + counterForEmit + " ListSize= " + numberSource.records.size());
										numberSource.setNumber(counterForEmit);
									}
								} else if (Objects.equals(parameterSerif.getValue(), "Максимум")) {
									float maxDBm = findMaxWithoutIgnore(arrays1,rangeForSerifs,dbLine,
											getFreq().getStartMHz(),getFFTBinHz().getValue());
									if (maxDBm != Float.NEGATIVE_INFINITY) {
										if (!signalDetected) {
											// Сигнал появился
											signalDetected = true;
                                        } else {
											try {
												FileWriter writer = new FileWriter("/home/user/gadalkaLogs/analizatorLogs/signallogs/"+hackrfID +".txt", true);
												writer.write("Сигнал все еще обнаружен");
												writer.append('\n');
												writer.close();
											} catch (IOException e) {
												System.out.println("Ошибка при записи в файл");
												e.printStackTrace();
											}
											// Сигнал продолжает быть видимым, можно обновлять максимум или игнорировать
											System.out.println("Сигнал все еще обнаружен");
                                        }
                                        missedIterations = 0;
                                        counterForEmit++;
                                        numberSource.records.add(new AzimuthDbm(numberSource.getCurrentAzimuth(), maxDBm));
                                        System.out.println("Signal detected! Counter = " + counterForEmit + ", ListSize = " + numberSource.records.size());
                                        numberSource.setNumber(counterForEmit);
                                    } else {
										if (signalDetected) {
											missedIterations++;
											try {
												FileWriter writer = new FileWriter("/home/user/gadalkaLogs/analizatorLogs/signallogs/"+ hackrfID +".txt", true);
												writer.write(missedIterations + ";");
												//writer.append('\n');
												writer.close();
											} catch (IOException e) {
												System.out.println("Ошибка при записи в файл");
												e.printStackTrace();
											}
											if (missedIterations >= parameterCounterLoseSignal.getValue()) {
												// Сигнал окончательно пропал
												signalDetected = false;
												missedIterations = 0;
												counterForEmit = 0;
												numberSource.setNumber(counterForEmit);
												try {
													FileWriter writer = new FileWriter("/home/user/gadalkaLogs/analizatorLogs/signallogs/"+ hackrfID +".txt", true);
													writer.write("Сигнал потерян");
													writer.append('\n');
													writer.close();
												} catch (IOException e) {
													System.out.println("Ошибка при записи в файл");
													e.printStackTrace();
												}
												System.out.println("Сигнал потерян");
											}
										}
									}
								} else if (Objects.equals(parameterSerif.getValue(), "Мульти максимум")) {
									int startFreq = getFreq().getStartMHz() * 1000000;
									int endFreq = getFreq().getEndMHz() * 1000000;
									int step = 5000000;
									int freqStep = getFFTBinHz().getStep();
									/*//List<FrequencyRangeDivider> ranges = divideAndCalculateAverages(startFreq, endFreq, step, arrays1, freqStep);

									List<SubRangeResult> ranges = divideAndCalculateMax(startFreq,endFreq,freqStep,arrays1, step,numberSource.getCurrentAzimuth());
									numberSource.setSubRangeResultList(ranges);
									if (listCounterForEmit == null) {
										listCounterForEmit = new ArrayList<>(ranges.size());
										for (int i = 0; i < ranges.size(); i++) {
											listCounterForEmit.add(0);
										}
										numberSource.setListCounterNumber(listCounterForEmit);
									}
									for (int i = 0; i< ranges.size(); i ++) {
										for (RangeForSerif range : rangeForSerifs) {
											if ( (double) ranges.get(i).getStartFreq() / 1000000 > range.getStartX() && (double) ranges.get(i).getEndFreq() / 1000000 < range.getEndX() && "ignore".equals(range.getStatus())) {
												continue;
											}
											if (ranges.get(i).getMaxDbm() > dbLine.getValue()){
												int currentValue = listCounterForEmit.get(i);
												listCounterForEmit.set(i, currentValue + 1);
												numberSource.setListCounterNumber(listCounterForEmit);
											}
										}
									}*/
									FrequencyRange range = getFreq();
									configSerif .setCurrentStartFrequency(range.getStartMHz());
									configSerif.setCurrentEndFrequency(range.getEndMHz());
									configSerif.setFrequencyStep(getFFTBinHz().getValue());
									configSerif.setSubRangeStep(parameterSubRange.getValue());
									configSerif.setAboveThresholdLimit(parameterCounterForSerif.getValue());
									configSerif.setBelowThresholdLimit(parameterCounterLoseSignal.getValue());
									configSerif.setMinDbLine(listMinLine);
									configSerif.setRangeForSerifs(rangeForSerifs);
									updateData(arrays1,numberSource.getCurrentAzimuth());
									/*divideAndTrackSignal(startFreq,
											endFreq,
											freqStep,
											step,
											arrays1,
											numberSource.getCurrentAzimuth()
											,dbLine.getValue(),
											numberSource.getCounterForSerif(),
											parameterCounterLoseSignal.getValue(),
											rangeForSerifs).subscribe(results -> {
												System.out.println("Сигнал исчез! Передаем данные:");
												results.forEach(System.out::println);
											},
											Throwable::printStackTrace,
											() -> System.out.println("Обработка завершена!"));*/
								}
								if (Objects.equals(parameterSerif.getValue(), "Середина")) {
									if (Objects.equals(oldCounter, counterForEmit)) {
										counterForEmit = 0;
										numberSource.setNumber(counterForEmit);
									}
									oldCounter = counterForEmit;
								}
								//System.out.println("counterforemit: " + counterForEmit);
								//System.out.print("oldcounter:" + oldCounter);
								chart.setNotify(true);

								if (gifCap != null) {
									gifCap.captureFrame();
								}
							});
						}

						synchronized (perfWatch) {
							perfWatch.hwFullSpectrumRefreshes++;
						}

						counter = 0;
					}

				} catch (InterruptedException e) {
					return;
				}
			}

		}

	}

	private void recalculateGains(int totalGain) {
		/**
		 * use only lna gain when <=40 when >40, add only vga gain
		 */
		int lnaGain = totalGain / 8 * 8; //lna gain has step 8, range <0, 40>
		if (lnaGain > 40)
			lnaGain = 40;
		int vgaGain = lnaGain != 40 ? 0 : ((totalGain - lnaGain) & ~1); //vga gain has step 2, range <0,60>
		this.parameterGainLNA.setValue(lnaGain);
		this.parameterGainVGA.setValue(vgaGain);
		this.parameterGainTotal.setValue(lnaGain + vgaGain);
	}

	/**
	 * uses fifo queue to process launch commands, only the last launch command
	 * is important, delete others
	 */
	private synchronized void restartHackrfSweep() {
		if (threadLaunchCommands.offer(0) == false) {
			threadLaunchCommands.clear();
			threadLaunchCommands.offer(0);
		}
	}

	/**
	 * no need to synchronize, executes only in the launcher thread
	 */
	private void restartHackrfSweepExecute() {
		stopHackrfSweep();
		threadHackrfSweep = new Thread(() -> {
			Thread.currentThread().setName("hackrf_sweep");
			try {
				forceStopSweep = false;
				sweep();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		threadHackrfSweep.start();
	}

	private void setupChart() {
		int axisWidthLeft = 70;
		int axisWidthRight = 20;

		chart = ChartFactory.createXYLineChart("Spectrum analyzer", "Частота [MHz]", "Мощность [dB]", chartDataset,
				PlotOrientation.VERTICAL, false, false, false);
		chart.getRenderingHints().put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		XYPlot plot = chart.getXYPlot();
		NumberAxis domainAxis = ((NumberAxis) plot.getDomainAxis());
		NumberAxis rangeAxis = ((NumberAxis) plot.getRangeAxis());
		chartLineRenderer = new XYLineAndShapeRenderer();
		chartLineRenderer.setBaseShapesVisible(false);
		chartLineRenderer.setBaseStroke(new BasicStroke(parameterSpectrumLineThickness.getValue().floatValue()));

		rangeAxis.setAutoRange(false);
		rangeAxis.setRange(-110, -10);
		rangeAxis.setTickUnit(new NumberTickUnit(10, new DecimalFormat("###")));

		domainAxis.setNumberFormatOverride(new DecimalFormat(" #.### "));

		chartLineRenderer.setAutoPopulateSeriesStroke(false);
		chartLineRenderer.setAutoPopulateSeriesPaint(false);
		chartLineRenderer.setSeriesPaint(0, colors.palette2);

		if (false)
			chart.addProgressListener(new ChartProgressListener() {
				StandardTickUnitSource tus = new StandardTickUnitSource();

				@Override
				public void chartProgress(ChartProgressEvent event) {
					if (event.getType() == ChartProgressEvent.DRAWING_STARTED) {
						Range r = domainAxis.getRange();
						domainAxis.setTickUnit((NumberTickUnit) tus.getCeilingTickUnit(r.getLength() / 20));
						domainAxis.setMinorTickCount(2);
						domainAxis.setMinorTickMarksVisible(true);

					}
				}
			});

		plot.setDomainGridlinesVisible(false);
		plot.setRenderer(chartLineRenderer);

		/**
		 * sets empty space around the plot
		 */
		AxisSpace axisSpace = new AxisSpace();
		axisSpace.setLeft(axisWidthLeft);
		axisSpace.setRight(axisWidthRight);
		axisSpace.setTop(0);
		axisSpace.setBottom(50);
		plot.setFixedDomainAxisSpace(axisSpace);//sets width of the domain axis left/right
		plot.setFixedRangeAxisSpace(axisSpace);//sets heigth of range axis top/bottom

		rangeAxis.setAxisLineVisible(false);
		rangeAxis.setTickMarksVisible(false);

		plot.setAxisOffset(RectangleInsets.ZERO_INSETS); //no space between range axis and plot

		Font labelFont = new Font(Font.MONOSPACED, Font.BOLD, 16);
		rangeAxis.setLabelFont(labelFont);
		rangeAxis.setTickLabelFont(labelFont);
		rangeAxis.setLabelPaint(colors.palette1);
		rangeAxis.setTickLabelPaint(colors.palette1);
		domainAxis.setLabelFont(labelFont);
		domainAxis.setTickLabelFont(labelFont);
		domainAxis.setLabelPaint(colors.palette1);
		domainAxis.setTickLabelPaint(colors.palette1);
		chartLineRenderer.setBasePaint(Color.white);
		plot.setBackgroundPaint(colors.palette4);
		chart.setBackgroundPaint(colors.palette4);
		chartLineRenderer.setSeriesPaint(1, colors.palette1);

		chartPanel = new ChartPanel(chart);
		chartPanel.setMaximumDrawWidth(4096);
		chartPanel.setMaximumDrawHeight(2160);
		chartPanel.setMouseWheelEnabled(false);
		chartPanel.setDomainZoomable(false);
		chartPanel.setRangeZoomable(false);
		chartPanel.setPopupMenu(null);
		chartPanel.setMinimumSize(new Dimension(200, 200));

		printInit(1);

		/**
		 * Draws overlay of waterfall's color scale next to main spectrum chart
		 * to show
		 */
		chartPanel.addOverlay(new Overlay() {
			@Override
			public void addChangeListener(OverlayChangeListener listener) {
			}

			@Override
			public void paintOverlay(Graphics2D g, ChartPanel chartPanel) {
				Rectangle2D area = chartPanel.getChartRenderingInfo().getPlotInfo().getDataArea();
				int plotStartX = (int) area.getX();
				int plotWidth = (int) area.getWidth();

				Rectangle2D subplotArea = chartPanel.getChartRenderingInfo().getPlotInfo().getDataArea();

				int y1 = (int) plot.getRangeAxis().valueToJava2D(waterfallPlot.getSpectrumPaletteStart(), subplotArea,
						plot.getRangeAxisEdge());
				int y2 = (int) plot.getRangeAxis().valueToJava2D(
						waterfallPlot.getSpectrumPaletteStart() + waterfallPlot.getSpectrumPaletteSize(), subplotArea,
						plot.getRangeAxisEdge());

				int x = plotStartX + plotWidth;
				int w = 15;
				int h = y1 - y2;
				waterfallPlot.drawScale(g, x, y2, w, h);
			}

			@Override
			public void removeChangeListener(OverlayChangeListener listener) {
			}
		});

		/**
		 * Draw frequency bands as an overlay
		 */
		if (true)
		chartPanel.addOverlay(new Overlay() {
			@Override
			public void addChangeListener(OverlayChangeListener listener) {
			}

			@Override
			public void paintOverlay(Graphics2D g2, ChartPanel chartPanel) {
				BufferedImage img = imageFrequencyAllocationTableBands;
				if (img != null) {
					Rectangle2D area = chartPanel.getChartRenderingInfo().getPlotInfo().getDataArea();
					g2.drawImage(img, (int) area.getX(), (int) area.getY(), null);
				}
			}

			@Override
			public void removeChangeListener(OverlayChangeListener listener) {
			}
		});

		/**
		 * monitors chart data area for change due to no other way to extract
		 * that info from jfreechart when it changes
		 */
		chart.addChangeListener(event -> {
			Rectangle2D aN = chartPanel.getChartRenderingInfo().getPlotInfo().getDataArea();
			Rectangle2D aO = chartDataArea.getValue();
			if (aO.getX() != aN.getX() || aO.getY() != aN.getY() || aO.getWidth() != aN.getWidth()
					|| aO.getHeight() != aN.getHeight()) {
				chartDataArea.setValue(new Rectangle2D.Double(aN.getX(), aN.getY(), aN.getWidth(), aN.getHeight()));
			}
		});

		chart.addProgressListener(new ChartProgressListener() {
			private long chartRedrawStarted;

			@Override
			public void chartProgress(ChartProgressEvent arg0) {
				if (arg0.getType() == ChartProgressEvent.DRAWING_STARTED) {
					chartRedrawStarted = System.nanoTime();
				} else if (arg0.getType() == ChartProgressEvent.DRAWING_FINISHED) {
					synchronized (perfWatch) {
						perfWatch.chartDrawing.addDrawingTime(System.nanoTime() - chartRedrawStarted);
					}
				}
			}
		});
		
		
	}

	/**
	 * Displays a cross marker with current frequency and signal strength when
	 * mouse hovers over the frequency chart
	 */
	private void setupChartMouseMarkers() {
		ValueMarker freqMarker = new ValueMarker(0, Color.WHITE, new BasicStroke(1f));
		freqMarker.setLabelPaint(Color.white);
		freqMarker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
		freqMarker.setLabelTextAnchor(TextAnchor.TOP_LEFT);
		freqMarker.setLabelFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
		ValueMarker signalMarker = new ValueMarker(0, Color.WHITE, new BasicStroke(1f));
		signalMarker.setLabelPaint(Color.white);
		signalMarker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
		signalMarker.setLabelTextAnchor(TextAnchor.BOTTOM_RIGHT);
		signalMarker.setLabelFont(new Font(Font.MONOSPACED, Font.BOLD, 16));

		chartPanel.addMouseMotionListener(new MouseMotionAdapter() {
			DecimalFormat format = new DecimalFormat("0.#");

			@Override
			public void mouseMoved(MouseEvent e) {
				int x = e.getX();
				int y = e.getY();

				XYPlot plot = chart.getXYPlot();
				Rectangle2D subplotArea = chartPanel.getChartRenderingInfo().getPlotInfo().getDataArea();
				double crosshairRange = plot.getRangeAxis().java2DToValue(y, subplotArea, plot.getRangeAxisEdge());
				signalMarker.setValue(crosshairRange);
				signalMarker.setLabel(String.format("%.1fdB", crosshairRange));
				double crosshairDomain = plot.getDomainAxis().java2DToValue(x, subplotArea, plot.getDomainAxisEdge());
				freqMarker.setValue(crosshairDomain);
				freqMarker.setLabel(String.format("%.1fMHz", crosshairDomain));

				FrequencyAllocationTable activeTable = parameterFrequencyAllocationTable.getValue();
				if (activeTable != null) {
					FrequencyBand band = activeTable.lookupBand((long) (crosshairDomain * 1000000l));
					if (band == null)
						titleFreqBand.setText(" ");
					else {
						titleFreqBand.setText(String.format("%s - %s MHz  %s", format.format(band.getMHzStartIncl()),
								format.format(band.getMHzEndExcl()), band.getApplications().replaceAll("/", " / ")));
					}
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				double currentX = e.getX();
				if (currentRectangle != null) {
					currentRectangle.setRect(
							Math.min(currentRectangle.getX(), currentX),
							currentRectangle.getY(),
							Math.abs(currentX - currentRectangle.getX()),
							currentRectangle.getHeight()
					);
				}
				chartPanel.repaint();
			}

		});
		chartPanel.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				double clickX = e.getX();
				double clickY = e.getY();
				int index = 0;

				// Используем итератор для удаления элементов из списка
				Iterator<ColoredRectangle> iterator = selectionRectangles.iterator();
				while (iterator.hasNext()) {
					ColoredRectangle coloredRect = iterator.next();
					Rectangle2D rect = coloredRect.rectangle;

					// Проверяем, был ли клик внутри текущего прямоугольника
					if (rect.contains(clickX, clickY)) {
						iterator.remove();  // Удаляем прямоугольник из списка
						rangeForSerifs.remove(index);  // Удаляем соответствующий диапазон
						chartPanel.repaint();
						break;
					}
					index++;
				}
				super.mouseClicked(e);
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				chart.getXYPlot().clearDomainMarkers();
				chart.getXYPlot().clearRangeMarkers();
				chart.getXYPlot().addRangeMarker(signalMarker);
				chart.getXYPlot().addDomainMarker(freqMarker);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				chart.getXYPlot().clearDomainMarkers();
				chart.getXYPlot().clearRangeMarkers();
				titleFreqBand.setText(" ");
			}

			@Override
			public void mousePressed(MouseEvent e) {
				int x = e.getX();
				XYPlot plot = chart.getXYPlot();
				startX = plot.getDomainAxis().java2DToValue(e.getX(), chartPanel.getScreenDataArea(), plot.getDomainAxisEdge());
				// Определяем цвет текущего выделения в зависимости от кнопки мыши
				if (SwingUtilities.isLeftMouseButton(e)) {
					currentColor = new Color(0, 0, 255, 50); // Синий для левой кнопки
				} else if (SwingUtilities.isRightMouseButton(e)) {
					currentColor = new Color(255, 0, 0, 50); // Красный для правой кнопки
				} else {
					currentColor = new Color(255, 0, 0, 50); // Красный для правой кнопки
				}
				currentRectangle = new Rectangle2D.Double(e.getX(), chartPanel.getScreenDataArea().getMinY(), 0, chartPanel.getScreenDataArea().getHeight());
				Rectangle2D subplotArea = chartPanel.getChartRenderingInfo().getPlotInfo().getDataArea();
				double crosshairDomain = plot.getDomainAxis().java2DToValue(x, subplotArea, plot.getDomainAxisEdge());
				//System.out.println("mouse pressed in " + String.format("%.1fMHz", crosshairDomain)+ " " + String.format("%.1fMHz", startX));
				super.mousePressed(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {

				int x = e.getX();
				XYPlot plot = chart.getXYPlot();
				endX = plot.getDomainAxis().java2DToValue(e.getX(), chartPanel.getScreenDataArea(), plot.getDomainAxisEdge());
				Rectangle2D subplotArea = chartPanel.getChartRenderingInfo().getPlotInfo().getDataArea();
				double crosshairDomain = plot.getDomainAxis().java2DToValue(x, subplotArea, plot.getDomainAxisEdge());
				if (currentRectangle != null && currentRectangle.getWidth() > 0) {
					selectionRectangles.add(new ColoredRectangle(currentRectangle, currentColor,startX,endX));  // Сохраняем текущий прямоугольник
				}
				currentRectangle = null;
				chartPanel.repaint();
				if (!Objects.equals(startX, endX)) {
					if (Objects.equals(currentColor, new Color(255, 0, 0, 50))) {
						addRangeForSerif(startX, endX, "ignore");
					} else {
						addRangeForSerif(startX, endX, "scan");
					}
				}
				//System.out.println("mouse released in " + String.format("%.1fMHz", crosshairDomain) + " " + String.format("%.1fMHz", endX));
				super.mouseReleased(e);
			}
		});
		chartPanel.addOverlay(new RangeSelectionOverlay());

		titleFreqBand.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		titleFreqBand.setPosition(RectangleEdge.BOTTOM);
		titleFreqBand.setHorizontalAlignment(HorizontalAlignment.LEFT);
		titleFreqBand.setMargin(0.0, 2.0, 0.0, 2.0);
		titleFreqBand.setPaint(Color.white);
		chart.addSubtitle(titleFreqBand);
	}

	private void setupFrequencyAllocationTable() {
		SwingUtilities.invokeLater(() -> {
			chartPanel.addComponentListener(new ComponentAdapter() {
				public void componentResized(ComponentEvent e) {
					Component[] qwe = e.getComponent().getParent().getComponents();
					//e.getComponent().setSize(e.getComponent().getWidth(),200);
					/*int height = e.getComponent().getHeight();
					if (height == 0) {
						qwe[0].setBounds(0,0,670,266);
						qwe[1].setBounds(0,271,670,299);
						qwe[2].setBounds(0,266,670,5);
					}*/
					chartPanel.repaint();
					if (chartPanel.isVisible() && chartPanel.getWidth() > 0 && chartPanel.getHeight() > 0) {
						redrawFrequencySpectrumTable();
					}

				}
				public void componentShown(ComponentEvent e) {
					System.out.println("show");
					splitPane.setDividerLocation(0.4);
					SwingUtilities.invokeLater(() -> {
						chartPanel.setPreferredSize(new Dimension(680, 266));
						chartPanel.setSize(new Dimension(680, 266));
						chartPanel.getParent().revalidate();
						chartPanel.getParent().repaint();
					});
				}

				public void componentHidden(ComponentEvent e) {
					System.out.println("hide");
					SwingUtilities.invokeLater(() -> {
						waterfallContainer.setMinimumSize(new Dimension(0, 0));
						waterfallContainer.setPreferredSize(null);
						splitPane.revalidate();
						splitPane.repaint();
						//waterfallPlot.setDrawingOffsets((int) area.getX(), (int) area.getWidth());
					});
				}
			});
			chart.getXYPlot().getDomainAxis().addChangeListener((e) -> {
				redrawFrequencySpectrumTable();
			});
			chart.getXYPlot().getRangeAxis().addChangeListener(event -> {
				redrawFrequencySpectrumTable();
				System.out.println(event);
			});

		});
		parameterFrequencyAllocationTable.addListener(this::redrawFrequencySpectrumTable);
	}

	private void setupParameterObservers() {
		Runnable restartHackrf = this::restartHackrfSweep;
		parameterFrequency.addListener(restartHackrf);
		parameterAntennaPolarization.addListener(restartHackrf);
		parameterAntennaLNA.addListener(restartHackrf);
		parameterFFTBinHz.addListener(restartHackrf);
		parameterSamples.addListener(restartHackrf);
		parameterIsCapturingPaused.addListener(this::fireCapturingStateChanged);

		parameterGainTotal.addListener((gainTotal) -> {
			if (flagManualGain) //flag is being adjusted manually by LNA or VGA, do not recalculate the gains
				return;
			recalculateGains(gainTotal);
			restartHackrfSweep();
		});
		Runnable gainRecalc = () -> {
			int totalGain = parameterGainLNA.getValue() + parameterGainVGA.getValue();
			flagManualGain = true;
			try {
				parameterGainTotal.setValue(totalGain);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				flagManualGain = false;
			}
			restartHackrfSweep();
		};
		parameterGainLNA.addListener(gainRecalc);
		parameterGainVGA.addListener(gainRecalc);

		parameterSpurRemoval.addListener(() -> {
			SpurFilter filter = spurFilter;
			if (filter != null) {
				filter.recalibrate();
			}
		});
		parameterShowPeaks.addListener(() -> {
			DatasetSpectrumPeak p = datasetSpectrum;
			if (p != null) {
				p.resetPeaks();
			}
		});
		parameterSpectrumPaletteStart.setValue((int) waterfallPlot.getSpectrumPaletteStart());
		parameterSpectrumPaletteSize.setValue((int) waterfallPlot.getSpectrumPaletteSize());
		parameterSpectrumPaletteStart.addListener((dB) -> {
			waterfallPlot.setSpectrumPaletteStart(dB);
			SwingUtilities.invokeLater(() -> {
				waterfallPaletteStartMarker.setValue(waterfallPlot.getSpectrumPaletteStart());
				waterfallPaletteEndMarker
						.setValue(waterfallPlot.getSpectrumPaletteStart() + waterfallPlot.getSpectrumPaletteSize());
			});
		});
		parameterSpectrumPaletteSize.addListener((dB) -> {
			if (dB < SPECTRUM_PALETTE_SIZE_MIN)
				return;
			waterfallPlot.setSpectrumPaletteSize(dB);
			SwingUtilities.invokeLater(() -> {
				waterfallPaletteStartMarker.setValue(waterfallPlot.getSpectrumPaletteStart());
				waterfallPaletteEndMarker
						.setValue(waterfallPlot.getSpectrumPaletteStart() + waterfallPlot.getSpectrumPaletteSize());
			});

		});
		parameterPeakFallRateSecs.addListener((fallRate) -> {
			datasetSpectrum.setPeakFalloutMillis(fallRate * 1000l);
		});

		parameterSpectrumLineThickness.addListener((thickness) -> {
			SwingUtilities.invokeLater(() -> chartLineRenderer.setBaseStroke(new BasicStroke(thickness.floatValue())));
		});

		parameterSerif.addListener((variant )-> {
			switch (variant){
				case "Середина":
					numberSource.setCurrentSerifVariant("Середина");
					break;
				case "Максимум":
					numberSource.setCurrentSerifVariant("Максимум");
					break;
				case "Мульти максимум":
					numberSource.setCurrentSerifVariant("Мульти максимум");
					break;
			}
		});

		parameterCounterForSerif.addListener((currentCounter) -> {
			switch (currentCounter) {
				case 5: numberSource.setCounterForSerif(5);
				case 10: numberSource.setCounterForSerif(10);
				case 15: numberSource.setCounterForSerif(15);
				case 20: numberSource.setCounterForSerif(20);
				case 25: numberSource.setCounterForSerif(25);
				case 30: numberSource.setCounterForSerif(30);
			}
		});

		parameterPersistentDisplayPersTime.addListener((time) -> {
			persistentDisplay.setPersistenceTime(time);
		});

		int persistentDisplayDownscaleFactor = 4;

		Runnable resetPersistentImage = () -> {
			boolean display = parameterPersistentDisplay.getValue();
			persistentDisplay.reset();
			chart.getXYPlot().setBackgroundImage(display ? persistentDisplay.getDisplayImage().getValue() : null);
			chart.getXYPlot().setBackgroundImageAlpha(alphaPersistentDisplayImage);
		};
		persistentDisplay.getDisplayImage().addListener((image) -> {
			if (parameterPersistentDisplay.getValue())
				chart.getXYPlot().setBackgroundImage(image);
		});

		registerListener(new HackRFEventAdapter() {
			@Override
			public void hardwareStatusChanged(boolean hardwareSendingData) {
				SwingUtilities.invokeLater(() -> {
					if (hardwareSendingData && parameterPersistentDisplay.getValue()) {
						resetPersistentImage.run();
					}
				});
			}
		});

		parameterPersistentDisplay.addListener((display) -> {
			SwingUtilities.invokeLater(resetPersistentImage::run);
		});

		chartDataArea.addListener((area) -> {
			SwingUtilities.invokeLater(() -> {
				/*
				 * Align the waterfall plot and the spectrum chart
				 */
				if (waterfallPlot != null)
					waterfallPlot.setDrawingOffsets((int) area.getX(), (int) area.getWidth());

				/**
				 * persistent display config
				 */
					persistentDisplay.setImageSize((int) area.getWidth() / persistentDisplayDownscaleFactor,
						(int) area.getWidth() / persistentDisplayDownscaleFactor);
				if (parameterPersistentDisplay.getValue()) {
					chart.getXYPlot().setBackgroundImage(persistentDisplay.getDisplayImage().getValue());
					chart.getXYPlot().setBackgroundImageAlpha(alphaPersistentDisplayImage);
				}
			});
		});
	}

	private void startLauncherThread() {
		threadLauncher = new Thread(() -> {
			Thread.currentThread().setName("Launcher-thread");
			while (true) {
				try {
					threadLaunchCommands.take();
					restartHackrfSweepExecute();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		threadLauncher.start();
	}

	/**
	 * no need to synchronize, executes only in launcher thread
	 */
	private void stopHackrfSweep() {
		forceStopSweep = true;
		if (threadHackrfSweep != null) {
			while (threadHackrfSweep.isAlive()) {
				forceStopSweep = true;
				//				System.out.println("Calling HackRFSweepNativeBridge.stop()");
				HackRFSweepNativeBridge.stop();
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
				}
			}
			try {
				threadHackrfSweep.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			threadHackrfSweep = null;
		}
		System.out.println("HackRFSweep thread stopped.");
		if (threadProcessing != null) {
			threadProcessing.interrupt();
			try {
				threadProcessing.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			threadProcessing = null;
			System.out.println("Processing thread stopped.");
		}
	}

	private void sweep() throws IOException {
		lock.lock();
		try {
			threadProcessing = new Thread(() -> {
				Thread.currentThread().setName("hackrf_sweep data processing thread");
				processingThread();
			});
			threadProcessing.start();

			/**
			 * Ensures auto-restart if HW disconnects
			 */
			while (forceStopSweep == false) {
				if (Objects.equals(hackrfID, ""))
					hackrfID = "123456789";
				azimuthPanel.getRange(getFreq().getStartMHz(),getFreq().getEndMHz());
				/*if (timerAzimuth == null)
					timerAzimuth = new Timer(azimuthPanel.frameDelay, e -> azimuthPanel.updateYPositions());
				if (timerAzimuth.isRunning()) {
					timerAzimuth.stop();
					timerAzimuth.setDelay(azimuthPanel.frameDelay);
				}
				timerAzimuth.start();*/
				FrequencyRange range = getFreq();

				SpectrumConfig config = new SpectrumConfig(
						range.getStartMHz(),
						range.getEndMHz(),
						parameterAntennaLNA.getValue(),
						getGainLNA().getValue(),
						getGainVGA().getValue(),
						parameterSpectrumPaletteStart.getValue(),
						parameterSpectrumPaletteSize.getValue(),
						getSamples().getValue(),
						getFFTBinHz().getValue(),
						parameterCounterForSerif.getValue(),
						parameterCounterLoseSignal.getValue(),
						parameterSubRange.getValue(),
						parameterAntennaPolarization.getValue()
				);



				socketIOGadalka.setSpectrumConfig(config);

				System.out.println(
						"Starting hackrf_sweep... " + getFreq().getStartMHz() + "-" + getFreq().getEndMHz() + "MHz ");
				System.out.println("hackrf_sweep params:  freq " + getFreq().getStartMHz() + "-" + getFreq().getEndMHz()
						+ "MHz  FFTBin " + parameterFFTBinHz.getValue() + "Hz  samples " + parameterSamples.getValue()
						+ "  lna: " + parameterGainLNA.getValue() + " vga: " + parameterGainVGA.getValue() + " antenna_lna: "+parameterAntennaLNA.getValue());
				fireHardwareStateChanged(false);
				HackRFSweepNativeBridge.start(this, getFreq().getStartMHz(), getFreq().getEndMHz(),
						parameterFFTBinHz.getValue(), parameterSamples.getValue(), parameterGainLNA.getValue(),
						parameterGainVGA.getValue(), parameterAntennaPolarization.getValue(), parameterAntennaLNA.getValue(),hackrfID);
				// Сюда в ставлять серийник !!!
				fireHardwareStateChanged(false);

				if (forceStopSweep == false) {
					Thread.sleep(1000);
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
			fireHardwareStateChanged(false);
		}
	}

	protected void redrawFrequencySpectrumTable() {
		Rectangle2D area = chartPanel.getChartRenderingInfo().getPlotInfo().getDataArea();
		FrequencyAllocationTable activeTable = parameterFrequencyAllocationTable.getValue();
		if (activeTable == null) {
			imageFrequencyAllocationTableBands = null;
		} else if (area.getWidth() > 0 && area.getHeight() > 0) {
			imageFrequencyAllocationTableBands = activeTable.drawAllocationTable((int) area.getWidth(),
					(int) area.getHeight(), alphaFreqAllocationTableBandsImage, getFreq().getStartMHz() * 1000000l,
					getFreq().getEndMHz() * 1000000l,
					//colors.palette4, 
					Color.white,
					//colors.palette1
					Color.DARK_GRAY);
		}
	}

	public static byte[] compress(byte[] input) {
		/*Deflater deflater = new Deflater();
		//deflater.setLevel(9);
		deflater.setInput(input);
		deflater.finish();

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		byte[] buffer = new byte[2048];

		while (!deflater.finished()) {
			int compressedSize = deflater.deflate(buffer);
			outputStream.write(buffer, 0, compressedSize);
		}*/
        byte[] compressedData = null;
        try {
            ByteArrayOutputStream byteStream =
                    new ByteArrayOutputStream(input.length);
            try {
                GZIPOutputStream zipStream =
                        new GZIPOutputStream(byteStream);
                try {
                    zipStream.write(input);
                } finally {
                    zipStream.close();
                }
            } finally {
                byteStream.close();
            }

            compressedData = byteStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return compressedData;
    }



	public void sendSerif(Integer azimuth){
		sett.setClicked(true);
		sett.serifMessage2(getFreq().getStartMHz(),getFreq().getEndMHz(),hackrfID,azimuth, -150);
	}

	// Класс для отображения выделения
	private class RangeSelectionOverlay implements Overlay {

		@Override
		public void paintOverlay(Graphics2D g2, ChartPanel chartPanel) {

			XYPlot plot = chart.getXYPlot();
			Rectangle2D dataArea = chartPanel.getScreenDataArea();

			// Рисуем все прямоугольники из списка
			for (ColoredRectangle rect : selectionRectangles) {

				double screenStartX = plot.getDomainAxis().valueToJava2D(rect.domainStartX, dataArea, plot.getDomainAxisEdge());
				double screenEndX = plot.getDomainAxis().valueToJava2D(rect.domainEndX, dataArea, plot.getDomainAxisEdge());
				rect.rectangle = new Rectangle2D.Double(
						Math.min(screenStartX, screenEndX),
						dataArea.getMinY(),
						Math.abs(screenEndX - screenStartX),
						dataArea.getHeight()
				);

				g2.setColor(rect.color);
				g2.fill(rect.rectangle);
				g2.setColor(rect.color.darker());
				g2.draw(rect.rectangle);

			}

			// Рисуем текущий прямоугольник выделения (при перетаскивании)
			if (currentRectangle != null) {
				g2.setColor(currentColor);  // Используем текущий цвет для временного прямоугольника
				g2.fill(currentRectangle);
				g2.setColor(currentColor.darker());
				g2.draw(currentRectangle);
			}
		}

		@Override
		public void addChangeListener(OverlayChangeListener overlayChangeListener) {

		}

		@Override
		public void removeChangeListener(OverlayChangeListener overlayChangeListener) {

		}
	}

	public void addRangeForSerif(Double startX, Double endX, String status){
		rangeForSerifs.add(new RangeForSerif(startX,endX,status));
	}

	private class ColoredRectangle {
		Rectangle2D rectangle;
		Color color;
		double domainStartX; // Начало в доменных координатах
		double domainEndX;   // Конец в доменных координатах

		public ColoredRectangle(Rectangle2D rectangle, Color color,double domainStartX, double domainEndX) {
			this.rectangle = rectangle;
			this.color = color;
			this.domainStartX = domainStartX;
			this.domainEndX = domainEndX;
		}
	}


	float findMaxWithoutIgnore(float[] arrays1, List<RangeForSerif> rangeForSerifs, ModelValueInt dbLine, double startFreqMHz, double fftBinHz) {
		double maxFreq = -1;
		float maxValue = Float.NEGATIVE_INFINITY;

		for (int i = 0; i < arrays1.length; i++) {
			double freq = (startFreqMHz * 1_000_000 + fftBinHz * i) / 1_000_000.0;

			// Проверяем, входит ли частота в диапазон с "ignore"
			boolean isIgnored = false;
			for (RangeForSerif range : rangeForSerifs) {
				if (freq > range.getStartX() && freq < range.getEndX() && "ignore".equals(range.getStatus())) {
					isIgnored = true;
					break;
				}
			}

			// Если частота не игнорируется и значение превышает текущий максимум
			if (!isIgnored && arrays1[i] > dbLine.getValue().floatValue() && arrays1[i] > maxValue) {
				maxValue = arrays1[i];
				maxFreq = freq;
			}
		}

		// Если нужно, возвращаем частоту или выводим информацию
		if (maxFreq != -1) {
			//System.out.println("Max value: " + maxValue + " at frequency: " + maxFreq + " MHz");
		} else {
			//System.out.println("No maximum found outside ignored ranges.");
		}

		return maxValue;// Можно вернуть частоту максимума, если это нужно
    }

	public static List<FrequencyRangeDivider> divideAndCalculateAverages(
			int startFreq, int endFreq, int step, float[] dbmValues, int freqStep) {
		List<FrequencyRangeDivider> ranges = new ArrayList<>();
		int numValues = dbmValues.length;

		int currentStart = startFreq;

		while (currentStart + step <= endFreq) {
			int currentEnd = currentStart + step;

			// Индексы в массиве dBm для текущего диапазона
			int startIndex = (currentStart - startFreq) / freqStep;
			int endIndex = Math.min((currentEnd - startFreq) / freqStep, numValues);

			// Рассчитываем средний dBm
			float sum = 0.0F;
			int count = 0;
			for (int i = startIndex; i < endIndex; i++) {
				sum += dbmValues[i];
				count++;
			}
			float averageDbm = count > 0 ? sum / count : (float) 0.0;

			// Добавляем диапазон с рассчитанным средним
			ranges.add(new FrequencyRangeDivider(currentStart, currentEnd, averageDbm));

			currentStart += step;
		}

		return ranges;
	}

	public static List<SubRangeResult> divideAndCalculateMax(int startFreq,
															 int endFreq,
															 int step,
															 float[] dbmValues,
															 int rangeStep,
															 int currentAzimuth) {
		List<SubRangeResult> results = new ArrayList<>();
		int subRangeSize = rangeStep / step; // количество точек (измерений) в одном поддиапазоне

		for (int i = startFreq, index = 0; i < endFreq; i += rangeStep) {
			int subRangeStart = i;
			int subRangeEnd = Math.min(i + rangeStep, endFreq);

			float maxDbm = Float.NEGATIVE_INFINITY;
			for (int j = 0; j < subRangeSize && (index + j) < dbmValues.length; j++) {
				maxDbm = Math.max(maxDbm, dbmValues[index + j]);
			}

			results.add(new SubRangeResult(subRangeStart, subRangeEnd, maxDbm, currentAzimuth));
			index += subRangeSize;
		}

		return results;
	}

	public static Observable<List<SubRangeResult>> divideAndTrackSignal(
			int startFreq, int endFreq, int freqStep, int step, float[] dbmValues, int azimuthValues,
			int threshold, int aboveThresholdLimit, int belowThresholdLimit,
			List<RangeForSerif> rangeForSerifs) {

		int subRangeSize = step / freqStep; // Количество точек в 5 МГц
		List<int[]> subRanges = new ArrayList<>();

		for (int i = startFreq; i < endFreq; i += step) {
			int subRangeEnd = Math.min(i + step, endFreq);
			subRanges.add(new int[]{i, subRangeEnd});
		}

		return Observable.fromIterable(subRanges)
				.subscribeOn(Schedulers.io())
				.map(range -> {
					int subRangeStart = range[0];
					int subRangeEnd = range[1];

					// Проверка на пересечение с вырезанными объектами
					for (RangeForSerif rangeForSerif : rangeForSerifs) {
						if ((double) subRangeStart / 1000000 > rangeForSerif.getStartX() &&
								(double) subRangeEnd / 1000000 < rangeForSerif.getEndX() &&
								"ignore".equals(rangeForSerif.getStatus())) {
							return null; // Если пересекается, пропускаем поддиапазон
						}
					}

					int startIndex = (subRangeStart - startFreq) / freqStep;
					int endIndex = Math.min(startIndex + subRangeSize, dbmValues.length);

					float maxDbm = Float.NEGATIVE_INFINITY;
					int maxDbmIndex = startIndex;

					for (int j = startIndex; j < endIndex; j++) {
						if (dbmValues[j] > maxDbm) {
							maxDbm = dbmValues[j];
							maxDbmIndex = j;
						}
					}

                    // Берем азимут для точки с максимальным dBm
                    SubRangeResult result = new SubRangeResult(subRangeStart, subRangeEnd, maxDbm, azimuthValues);

					// Логика счетчиков
					if (maxDbm > threshold) {
						result.aboveThresholdCount++;
						result.belowThresholdCount = 0;
					} else if (result.aboveThresholdCount >= aboveThresholdLimit) {
						result.belowThresholdCount++;
						if (result.belowThresholdCount >= belowThresholdLimit) {
							result.aboveThresholdCount = 0;
							result.belowThresholdCount = 0;
							return result; // Передаем результат при исчезновении сигнала
						}
					}

					return null; // Если нет исчезновения сигнала — пропускаем
				})
				.filter(Objects::nonNull)
				.buffer(subRanges.size()); // Собираем все исчезнувшие диапазоны в список
	}

	// Метод для передачи новых данных
	public void updateData(float[] newDbmValues, int azimuthValue) {
		SignalData signalData = new SignalData(newDbmValues, azimuthValue);
		signalStreamProcessor.addDataToBuffer(signalData);  // Добавляем данные в буфер для обработки
	}

	// Метод для добавления содержимого одного файла в другой
	private static void appendFile(File source, File target) throws IOException {
		try (BufferedReader reader = new BufferedReader(new FileReader(source));
			 BufferedWriter writer = new BufferedWriter(new FileWriter(target, true))) { // true → дозапись
			String line;
			while ((line = reader.readLine()) != null) {
				writer.write(line);
				writer.newLine();
			}
		}catch (Exception e){
			System.out.println(e.getMessage());
		}
	}

	private static void deleteOldLogs(String logsDirPath){
		String homeDir = System.getProperty("user.home");
		String logsDirPathAbsolute = homeDir + logsDirPath; // Путь к папке с логами
		int daysThreshold = 30; // Удалять файлы старше 30 дней

		File logsDir = new File(logsDirPathAbsolute);
		if (!logsDir.exists() || !logsDir.isDirectory()) {
			System.out.println("Папка логов не найдена: " + logsDirPathAbsolute);
			return;
		}

		File[] files = logsDir.listFiles();
		if (files == null) {
			System.out.println("Нет файлов для проверки.");
			return;
		}

		long now = System.currentTimeMillis();
		for (File file : files) {
			try {
				// Получаем атрибуты файла (включая дату последнего изменения)
				BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
				long fileTime = attr.lastModifiedTime().toMillis();

				// Вычисляем разницу в днях
				long daysDiff = TimeUnit.MILLISECONDS.toDays(now - fileTime);

				if (daysDiff > daysThreshold) {
					if (file.delete()) {
						System.out.println("Удалён файл: " + file.getName() + " (был старше " + daysThreshold + " дней)");
					} else {
						System.out.println("Не удалось удалить: " + file.getName());
					}
				}
			} catch (Exception e) {
				System.out.println("Ошибка при обработке файла: " + file.getName());
				e.printStackTrace();
			}
		}
	}

	private void saveConfig() {
		JSONArray jsonArray = new JSONArray();
		for (RangeForSerif range: rangeForSerifs){
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("startfreq", range.getStartX());
			jsonObject.put("endfreq", range.getEndX());
			jsonObject.put("status", range.getStatus());
			jsonArray.add(jsonObject);
		}
		LinkedHashMap<String, Object> chartSettings = new LinkedHashMap<>();
		chartSettings.put("serifvariant",parameterSerif.getValue());
		chartSettings.put("subrange",parameterSubRange.getValue());
		chartSettings.put("serifline",dbLine.getValue());
		chartSettings.put("fftbinhz",parameterFFTBinHz.getValue());
		chartSettings.put("samples",parameterSamples.getValue());
		chartSettings.put("counterserif",parameterCounterForSerif.getValue());
		chartSettings.put("counterloseserif",parameterCounterLoseSignal.getValue());
		chartSettings.put("showpeaks",parameterShowPeaks.getValue());
		chartSettings.put("timeshowpeaks",parameterPeakFallRateSecs.getValue());
		chartSettings.put("spurfilter",parameterFilterSpectrum.getValue());
		chartSettings.put("linethickness",parameterSpectrumLineThickness.getValue().intValue());
		chartSettings.put("persistencedisplay",parameterPersistentDisplay.getValue());
		chartSettings.put("timepersistencedisplay",parameterPersistentDisplayPersTime.getValue());
		chartSettings.put("aim",parameterIsCrossEnabled.getValue());
		chartSettings.put("debugdisplay",parameterDebugDisplay.getValue());
		LinkedHashMap<String, Object> hackrfSettings = new LinkedHashMap<>();
		hackrfSettings.put("freqstart",getFreq().getStartMHz());
		hackrfSettings.put("freqend",getFreq().getEndMHz());
		hackrfSettings.put("gainlna",parameterGainLNA.getValue());
		hackrfSettings.put("gainvga",parameterGainVGA.getValue());
		hackrfSettings.put("totalgain",parameterGainTotal.getValue());
		hackrfSettings.put("startwaterfallpalette",parameterSpectrumPaletteStart.getValue());
		hackrfSettings.put("sizewaterfallpalette",parameterSpectrumPaletteSize.getValue());
		hackrfSettings.put("antennalna",parameterAntennaLNA.getValue());
		hackrfSettings.put("antennapolarization", parameterAntennaPolarization.getValue());
		LinkedHashMap<String, Object> generalSettings = new LinkedHashMap<>();
		generalSettings.put("numberapp",numberApp);
		generalSettings.put("hackrfid",hackrfID);
		//obj1.put("uid", uid);
		generalSettings.put("ipadress",ipadress);
		generalSettings.put("udpport",port);
		generalSettings.put("hackrfsettings", hackrfSettings);
		generalSettings.put("chartsettings", chartSettings);
		generalSettings.put("ranges",jsonArray);
		System.out.println(generalSettings);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		try (FileWriter file = new FileWriter(pathFile)) {
			gson.toJson(generalSettings, file);
			file.flush();
		} catch (IOException ex) {
			System.out.println(ex.getMessage());
		}
	}
}
