package jspectrumanalyzer.ui;

import io.reactivex.rxjava3.disposables.Disposable;
import jspectrumanalyzer.HackRFSweepSpectrumAnalyzer;
import jspectrumanalyzer.core.DatasetSpectrum;
import jspectrumanalyzer.core.EMA;
import jspectrumanalyzer.enciderinterface.SocketDataListener;
import jspectrumanalyzer.socket.SocketIOGadalka;
import org.jfree.chart.ChartPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class WaterfallPlot extends JPanel {
	/**
	 *
	 */
	private static final long	serialVersionUID		= 3249110968962287324L;
	private BufferedImage		bufferedImages[]		= new BufferedImage[2];
	private int					chartXOffset			= 0, chartWidth = 100;
	private boolean				displayMarker			= false;
	private double				displayMarkerFrequency	= 0;
	private int					displayMarkerX			= 0;
	private int displayMarkerY = 0;
	private int					drawIndex				= 0;
	/**
	 * stores max value in pixel
	 */
	private float				drawMaxBuffer[];
	private EMA					fps						= new EMA(3);
	private int					fpsRenderedFrames		= 0;
	private long				lastFPSRecalculated		= 0;
	private DatasetSpectrum		lastSpectrum			= null;
	private ColorPalette		palette					= new HotIronBluePalette();
	private Rectangle2D.Float	rect					= new Rectangle2D.Float(0f, 0f, 1f, 1f);
	private String				renderingInfo			= "";
	private int					screenWidth;
	private double				spectrumPaletteSize		= 65;
	private double				spectrumPaletteStart	= -90;
	private String[]			statusMessage			= new String[4];
	private final Disposable subscription;

	private Graphics2D g;
	private SocketIOGadalka socketIO;
	private SocketDataListener listener;
	private LinkedList<AzimuthPanel.AzimuthValue> azimuthValues ;
	private Integer WPAzimuth;
	private List<Integer> arrayAzimuth = new ArrayList<>();
	private Integer testValue = 0;

	private BufferedImage azimuthImage;
	private Graphics2D gAzimuth;
	private int azimuthYPosition = 0; // Текущая позиция для отрисовки азимута
	private final int azimuthSpacing = 1; // Отступ между значениями азимута
	private List<String> azimuthTexts = new ArrayList<>(); // Список значений азимутов
	private Integer updateCounter = 0;
	private AzimuthPanel azimuthPanel;
	int squareSize = 150; // Размер квадрата
	private int brightestXFinal = -1;
	private int brightestYFinal = -1;
	private boolean isCross = false;
	private List<float[]> array = new ArrayList<>();
	private boolean secondParameter = true;
	private boolean isFullScreen = false;


	private int width = (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth();

	public WaterfallPlot(ChartPanel chartPanel, int maxHeight, HackRFSweepSpectrumAnalyzer hackRFSweepSpectrumAnalyzer, SocketIOGadalka socketIOGadalka) {

		this.socketIO = socketIOGadalka;

		setPreferredSize(new Dimension(100, 2000)); // высота водопада изначально
		setMinimumSize(new Dimension(100, 200));

		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				setHistorySize(getHeight());
				if (e.getComponent().getWidth() > 1000){
					setDrawingOffsets(78, 1534);
					isFullScreen = true;
				}else {
					setDrawingOffsets(78, 574);
					isFullScreen = false;
				}
			}
		});

		screenWidth = (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth();
		drawMaxBuffer = new float[screenWidth];

		bufferedImages[0] = GraphicsToolkit.createAcceleratedImageOpaque(screenWidth, maxHeight);
		bufferedImages[1] = GraphicsToolkit.createAcceleratedImageOpaque(screenWidth, maxHeight);
		azimuthPanel = hackRFSweepSpectrumAnalyzer.azimuthPanel;



		/**
		 * setup frequency marker
		 */
		addMouseMotionListener(new MouseMotionAdapter() {

			@Override
			public void mouseMoved(MouseEvent e) {
				displayMarker = false;
				int x = e.getX();
				int y = e.getY();
				if (x < chartXOffset || x > chartXOffset + chartWidth) {
					return;
				}
				double freq = translateChartXToFrequency(x - chartXOffset);
				if (freq != -1) {
					displayMarker = true;
					displayMarkerFrequency = freq;
					displayMarkerX = x;
					displayMarkerY = y;
				}
				WaterfallPlot.this.repaint();
			}
		});

		addMouseWheelListener(e -> {
            int rotation = e.getWheelRotation();

            // Увеличиваем или уменьшаем размер квадрата
            if (rotation < 0) {
                squareSize += 5; // Увеличение
            } else {
                squareSize -= 5; // Уменьшение
            }

            // Ограничиваем размер квадрата
            squareSize = Math.max(30, Math.min(150, squareSize));

            // Перерисовываем компонент
            repaint();
        });

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				azimuthValues = hackRFSweepSpectrumAnalyzer.azimuthPanel.getAzimuthValues();
				super.mouseEntered(e);
			}


			@Override
			public void mouseExited(MouseEvent e) {
				displayMarker = false;
			}
			@Override
			public synchronized void mouseClicked(MouseEvent e) {
				super.mouseClicked(e);
				if (isCross) {



					int searchRadius = squareSize / 2; // Область поиска яркого пикселя

					int brightestY = e.getY();

					if (secondParameter) {
						int plusSumsize = 0;
						if (isFullScreen)
							plusSumsize = 121;
						else
							plusSumsize = 601;
						int brightestX = (e.getX() + plusSumsize);

						int maxBrightness = 0;
						int brX = 0;
						int brY = 0;
						float minBrightness = -200;
						for (int i = brightestY - searchRadius; i <= brightestY + searchRadius; i++) {
							float[] arrayF = array.get(i);
							for (int j = brightestX - searchRadius; j < brightestX + searchRadius; j++) {
								if (arrayF[j] > minBrightness) {
									minBrightness = arrayF[j];
									brX = j;
									brY = i;
								}
							}

						}
						if (isFullScreen)
							brightestXFinal = (brX -120);
						else
							brightestXFinal = (brX - 601);
						brightestYFinal = brY;
					} else {

						int brightestX = e.getX();
						int maxBrightness = 0;
						// Перебираем область вокруг клика
						for (int dx = -searchRadius; dx <= searchRadius; dx++) {
							for (int dy = -searchRadius; dy <= searchRadius; dy++) {
								int x = e.getX() + dx;
								int y = e.getY() + dy;

								if (x >= 0 && x < bufferedImages[drawIndex].getWidth() &&
										y >= 0 && y < bufferedImages[drawIndex].getHeight()) {

							/*int rgb = bufferedImages[drawIndex].getRGB(x, y);
							int r = (rgb >> 16) & 0xFF;
							int g = (rgb >> 8) & 0xFF;
							int b = rgb & 0xFF;
							int brightness = r + g + b; // Оценка яркости*/

									int[] brightnessMap = computePaletteBrightness(palette);

									int rgb = bufferedImages[drawIndex].getRGB(x, y);
									int brightness = getBrightnessWithPalette(rgb, palette, brightnessMap);

									if (brightness > maxBrightness) {
										maxBrightness = brightness;
										brightestX = x;
										brightestY = y;
									}
								}
							}
						}
						brightestXFinal = brightestX;
						brightestYFinal = brightestY;
					}

					//displayMarkerX = brightestX;
					//displayMarkerY = brightestY;

					LinkedList<AzimuthPanel.AzimuthValue> azimuthValues = hackRFSweepSpectrumAnalyzer.azimuthPanel.getAzimuthValues();
					int target = brightestY;
					int azimuth1 = arrayAzimuth.get(target);
					int closestNumber = azimuthValues.getFirst().yPosition;
					int azimuth = 0;
					int minDifference = Math.abs(target - closestNumber);
					try {
						for (AzimuthPanel.AzimuthValue value : azimuthValues) {
							int difference = Math.abs(target - value.yPosition);
							if (difference < minDifference) {
								minDifference = difference;
								closestNumber = value.yPosition;
								azimuth = value.azimuth;
							}
						}
						System.out.println(azimuth);
						System.out.println(azimuth1);
						hackRFSweepSpectrumAnalyzer.sendSerif(azimuth1);
					} catch (Exception ex) {
						System.out.println(ex.getMessage());
					}
					//repaint();
				} else {
					LinkedList<AzimuthPanel.AzimuthValue> azimuthValues = hackRFSweepSpectrumAnalyzer.azimuthPanel.getAzimuthValues();
					int target = e.getY();
					int targetX = e.getX();
					int azimuth1 = arrayAzimuth.get(target);
					int closestNumber = azimuthValues.getFirst().yPosition;
					int azimuth = 0;
					int minDifference = Math.abs(target - closestNumber);
					try {
						for (AzimuthPanel.AzimuthValue value : azimuthValues) {
							int difference = Math.abs(target - value.yPosition);
							if (difference < minDifference) {
								minDifference = difference;
								closestNumber = value.yPosition;
								azimuth = value.azimuth;
							}
						}
						System.out.println(azimuth);
						System.out.println(azimuth1);
						hackRFSweepSpectrumAnalyzer.sendSerif(azimuth1);
					} catch (Exception ex) {
						System.out.println(ex.getMessage());
					}
				}
			}

		});

		listener = new SocketDataListener(socketIO);
		subscription = listener.getAzimuthObservable().subscribe(
				azimuth -> {
					//azimuth = testValue;
					WPAzimuth = azimuth;
					//testValue += 2;
					//if (testValue >= 180)
					//	testValue = 0;
				},
				Throwable::printStackTrace
		);

		// Добавление цифр синхронно с отрисовкой statusMessage
//		int numberXOffset = chartXOffset + chartWidth; // Смещение для отображения столбика справа от основного изображения

	/*	String path = "/home/androkroker/azimut/azimut.txt";

		RxFileReader azimuthRead = new RxFileReader(path);
		azimuthRead.startReading();

		Consumer<String> customHandler = content -> {
			//System.out.println("waterfall = " + content);

			if (drawIndex == 1) {
				drawIndex = 0;
			} else {
				drawIndex = 1;
			}
			int width = (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth();
			g = bufferedImages[drawIndex].createGraphics();
			g.setFont(new Font("Arial", Font.BOLD, 65));
			g.drawString(content, width - 150, 50);
		};

		// Создаем и запускаем слушателя
		FileListener fileListener = new FileListener(azimuthRead);
		fileListener.startListening(customHandler,hackRFSweepSpectrumAnalyzer);
*/



//        int diap = ((1000)/1000); //задать разницу диапахона частот



//		fileReader.getObservableFile(path)
//				.delaySubscription(3L, TimeUnit.SECONDS)
//				.subscribe(
//						content -> {
//							SwingUtilities.invokeLater(() -> {
//								System.out.println("content is rx = " + content);
//
//								if (drawIndex == 1){
//									drawIndex = 0;
//								}
//								else {
//									drawIndex = 1;
//								}
//								int width = (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth();
//								g = bufferedImages[drawIndex].createGraphics();
//								g.setFont(new Font("Arial",Font.BOLD, 65));
//								g.drawString(content, width - 150, 50);
//							});
//						},
//						throwable -> {
//							System.out.println("content is rx throw = " + throwable.getMessage());
//						}
//				);

//		try {
//			socketTest = new SocketTest();
//
//		} catch (Exception e) {
//			throw new RuntimeException(e);
//		}
	}

	private EMA newDataTimeEMA =	 new EMA(100);
	/**
	 * Adds new data to the waterfall plot and renders it
	 *
	 * @param spectrum
	 */
	public synchronized void addNewData(DatasetSpectrum spectrum) {
		arrayAzimuth.addFirst(WPAzimuth);
		//float [] currentArray = spectrum.getSpectrumArray();
		//array.addFirst(Arrays.copyOf(currentArray, currentArray.length));
		int size = spectrum.spectrumLength();
		double startFreq = spectrum.getFreqStartMHz() * 1000000d;
		double freqRange = (spectrum.getFreqStopMHz() - spectrum.getFreqStartMHz()) * 1000000d;
		double width = bufferedImages[0].getWidth();
		double spectrumPalleteMax = spectrumPaletteStart + spectrumPaletteSize;

		this.lastSpectrum = spectrum;

		/**
		 * shift image by one pixel down
		 */
		BufferedImage previousImage = bufferedImages[drawIndex];
		drawIndex = (drawIndex + 1) % 2;
		g = bufferedImages[drawIndex].createGraphics();

		azimuthPanel.updateYPositions();

		g.drawImage(previousImage, 0, 1, null);
		g.setColor(Color.black);
		g.fillRect(0, 0, (int) width, 1);

		float binWidth = (float) (spectrum.getFFTBinSizeHz() / freqRange * width);
		rect.x = 0;
		rect.y = 0;
		rect.height = 0;
		rect.width = binWidth;

		float minimumValueDrawBuffer = -150;
		Arrays.fill(drawMaxBuffer, minimumValueDrawBuffer);

		/**
		 * draw in two passes - first determines maximum power for the pixel,
		 * second draws it
		 */
		if (true) {
			//optimized drawing
			double widthDivSize = (double)width / size;
			double inverseSpectrumPaletteSize	= 1d/spectrumPaletteSize;
			double spectrumPaletteStartDivSpectrumPaletteSize	= (double)spectrumPaletteStart/spectrumPaletteSize;
			for (int i = 0; i < size; i++) {
				double power = spectrum.getPower(i);
				double percentagePower	= 0;
				if (power > spectrumPaletteStart) {
					if ( power < spectrumPalleteMax) {
//						percentagePower	= (power - spectrumPaletteStart) / spectrumPaletteSize;
						//percentagePower	= power/spectrumPaletteSize - spectrumPaletteStart/spectrumPaletteSize;
						percentagePower	= power*inverseSpectrumPaletteSize - spectrumPaletteStartDivSpectrumPaletteSize;
					}
					else
						percentagePower = 1;
				}
				int pixelX = (int) Math.round(widthDivSize * i);
				pixelX = pixelX >= drawMaxBuffer.length ? drawMaxBuffer.length - 1 : pixelX < 0 ? 0 : pixelX;
				if (percentagePower > drawMaxBuffer[pixelX])
					drawMaxBuffer[pixelX] = (float) percentagePower;
			}
		} else {
			//unoptimized drawing
			for (int i = 0; i < size; i++) {
				double freq = spectrum.getFrequency(i);
				double power = spectrum.getPower(i);
				double percentageFreq = (freq - startFreq) / freqRange;
				double percentagePower = power < spectrumPaletteStart ? 0
						: power > spectrumPalleteMax ? 1 : (power - spectrumPaletteStart) / spectrumPaletteSize;
				int pixelX = (int) Math.round(width * percentageFreq);
				pixelX = pixelX >= drawMaxBuffer.length ? drawMaxBuffer.length - 1 : pixelX < 0 ? 0 : pixelX;
				if (percentagePower > drawMaxBuffer[pixelX])
					drawMaxBuffer[pixelX] = (float) percentagePower;
			}
		}

		/**
		 * fill in pixels that do not have power with last bin's color in order
		 * to smooth the spectrum
		 * окрашивание водопада
		 */
		array.addFirst(Arrays.copyOf(drawMaxBuffer,drawMaxBuffer.length));
		if (array.size() > 3000)
			array.removeLast();
		Color lastValidColor = palette.getColor(0);
		for (int x = 0; x < drawMaxBuffer.length; x++) {
			Color color;
			if (drawMaxBuffer[x] == minimumValueDrawBuffer)
				color = lastValidColor;
			else {
				color = palette.getColorNormalized(drawMaxBuffer[x]);
				lastValidColor = color;
			}
			rect.x = x;
			g.setColor(color); //цвет точки водопада
			try {
				g.draw(rect);
			} catch (Exception e) {
				System.out.println(e.getMessage());


			}
		}


		g.setColor(Color.white);//цвет текста азимута
		if (stringBounds == null)
			stringBounds = g.getFontMetrics().getStringBounds("TEST", g);
		int fontHeight = (int) stringBounds.getHeight();

		int h = getHeight();
		// Начальное значение y
		int y = h - fontHeight * 50; // Смещаем начальную позицию для отображения всех строк
//		int x = chartXOffset + w - 700;

//		g.drawString(renderingInfo, x, y);


		renderingInfo = String.format("RBW %.1fkHz / FFT bins: %d%s / %.1ffps",
				lastSpectrum == null ? 0 : lastSpectrum.getFFTBinSizeHz() / 1000d, size >= 10000 ? size / 1000 : size,
				size >= 10000 ? "k" : "", fps.getEma());
		fpsRenderedFrames++;
		if (System.currentTimeMillis() - lastFPSRecalculated > 1000) {
			double rawfps = fpsRenderedFrames / ((System.currentTimeMillis() - (double) lastFPSRecalculated) / 1000d);
			fps.addNewValue(rawfps);
			lastFPSRecalculated = System.currentTimeMillis();
			fpsRenderedFrames = 0;
		}
		g.dispose();

//		double time	= newDataTimeEMA.addNewValue(((System.nanoTime()-start)/1000));
//		System.out.println("draw "+(int)time+"us");

//		repaint();
	}

	/**
	 * Draws color palette into given area from bottom (0%) to top (100%)
	 *
	 * @param g
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 */
	public void drawScale(Graphics2D g, int x, int y, int w, int h) {
		g = (Graphics2D) g.create(x, y, w, h);
		int step = 3;
		for (int i = 0; i < h; i += step) {
			Color c = palette.getColorNormalized(1 - (double) i / h);
			g.setColor(c); //цвет\градиент полоски силы частоты
			g.fillRect(0, i, w, step);
		}

		/**
		 * draw border around the scale
		 */
		int thickness = 2;
		g.setColor(Color.darkGray);
		g.fillRect(0, 0, w, thickness);
		g.fillRect(w - thickness, 0, thickness, h);
		g.fillRect(0, h - thickness, w, thickness);
		g.dispose();
	}

	public int getHistorySize() {
		return bufferedImages[0].getHeight();
	}

	public double getSpectrumPaletteSize() {
		return spectrumPaletteSize;
	}

	public double getSpectrumPaletteStart() {
		return spectrumPaletteStart;
	}
	public void setSpectrumPaletteSize(double spectrumPaletteSize) {
		this.spectrumPaletteSize = spectrumPaletteSize;
	}

	public void setSpectrumPaletteStart(double spectrumPaletteStart) {
		this.spectrumPaletteStart = spectrumPaletteStart;
	}

	public void setDrawingOffsets(int xOffsetLeft, int width) {
		this.chartXOffset = xOffsetLeft;
		this.chartWidth = width;
	}

	public synchronized void setHistorySize(int historyInPixels) {
		BufferedImage bufferedImages[] = new BufferedImage[2];
		bufferedImages[0] = GraphicsToolkit.createAcceleratedImageOpaque(screenWidth, historyInPixels);
		bufferedImages[1] = GraphicsToolkit.createAcceleratedImageOpaque(screenWidth, historyInPixels);
		copyImage(this.bufferedImages[0], bufferedImages[0]);
		copyImage(this.bufferedImages[1], bufferedImages[1]);
		this.bufferedImages = bufferedImages;
	}

	public void setSpectrumPaletteSize(int dB) {
		this.spectrumPaletteSize = dB;
	}

	/**
	 * Sets start and end of the color scale
	 *

	 */
	public void setSpectrumPaletteStart(int dB) {
		this.spectrumPaletteStart = dB;
	}

	/**
	 * Sets status message to be drawn near bottom right corner
	 *
	 * @param message
	 * @param index
	 *            max array length is 4
	 */
	public void setStatusMessage(String message, int index) {
		this.statusMessage[index] = message;
	}

	private void copyImage(BufferedImage src, BufferedImage dst) {
		Graphics2D g = dst.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
	}

	private double translateChartXToFrequency(int x) {
		if (lastSpectrum != null) {
			double startFreq = lastSpectrum.getFreqStartMHz() * 1000000d;
			double stopFreq = lastSpectrum.getFreqStopMHz() * 1000000d;
			double freqRange = (stopFreq - startFreq);
			double width = bufferedImages[0].getWidth();
			double percentageFreq = x / (double) chartWidth;
			double freq = percentageFreq * freqRange + startFreq;
			if (freq > stopFreq)
				freq = stopFreq;
			if (freq < startFreq)
				freq = startFreq;
			return freq;
		}
		return -1;
	}

	/**
	 * Отрисовка компонентов
	 */
	Rectangle2D stringBounds;
	@Override
	protected void paintComponent(Graphics arg0) {
		long drawStart = System.nanoTime();
		Graphics2D g = (Graphics2D) arg0;
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		int w = chartWidth;
		int h = getHeight();
		g.setColor(Color.black);
		g.fillRect(0, 0, getWidth(), getHeight());

		g.drawImage(bufferedImages[drawIndex], chartXOffset, 0, w, h, null);

		// Отрисовка маркера (вертикальная полоса с частотой)
		if (displayMarker) {


			int target = displayMarkerY;
			int closestNumber = azimuthValues.getFirst().yPosition;
			int azimuth = 0;
			int minDifference = Math.abs(target - closestNumber);
			try {
				for (AzimuthPanel.AzimuthValue value : azimuthValues) {
					int difference = Math.abs(target - value.yPosition);
					if (difference < minDifference) {
						minDifference = difference;
						closestNumber = value.yPosition;
						azimuth = value.azimuth;
					}
				}
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}

			if (isCross) {
				//int squareSize = 100; // Размер квадрата
				int halfSize = squareSize / 2;
				g.setColor(Color.WHITE);
				g.drawRect(displayMarkerX - squareSize / 2, displayMarkerY - squareSize / 2, squareSize, squareSize);

				g.setColor(Color.white);
				int fontSize = 18; // Установите желаемый размер шрифта
				g.setFont(new Font("Default", Font.PLAIN, fontSize));
				g.drawLine(80, displayMarkerY, displayMarkerX - halfSize, displayMarkerY);
				g.drawLine(displayMarkerX + halfSize, displayMarkerY, chartWidth + 100, displayMarkerY);

				// Вертикальная линия (верхняя и нижняя часть)
				g.drawLine(displayMarkerX, 0, displayMarkerX, displayMarkerY - halfSize);
				g.drawLine(displayMarkerX, displayMarkerY + halfSize, displayMarkerX, getHeight());
				g.drawString(String.format("%.1fMHz", displayMarkerFrequency / 1000000.0), displayMarkerX + 5, h / 2);
				g.drawString(String.valueOf(azimuth), displayMarkerX - 35, displayMarkerY - 5);


				if (brightestXFinal != -1 && brightestYFinal != -1) {
					g.setColor(Color.WHITE);
					g.drawLine(brightestXFinal - 2, brightestYFinal, brightestXFinal + 2, brightestYFinal);
					g.drawLine(brightestXFinal, brightestYFinal - 2, brightestXFinal, brightestYFinal + 2);

					//brightestXFinal = -1;
					//brightestYFinal = -1;

				}
			} else {


				g.setColor(Color.white);
				int fontSize = 18; // Установите желаемый размер шрифта
				g.setFont(new Font("Default", Font.PLAIN, fontSize));
				g.drawLine(displayMarkerX, 0, displayMarkerX, h);
				g.drawLine(80, displayMarkerY, w + 100, displayMarkerY);
				g.drawString(String.format("%.1fMHz", displayMarkerFrequency / 1000000.0), displayMarkerX + 5, h / 2);
				g.drawString(String.valueOf(azimuth), displayMarkerX - 35, displayMarkerY - 5);
			}



		} // finish marker


		//параллельный вывод
/*
		Thread writeData = new Thread(()->{
			try {
					for (int i = 0; i < 100; i++) {
						g.drawString(String.valueOf(dataProcessor.processLastLine()), numberXOffset, y + fontHeight * (i + 1));
					}
					Thread.sleep(1000);

			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		});

		writeData.start();
*/

		long drawingTime = System.nanoTime() - drawStart;
		drawingTimeSum += drawingTime;
		drawingCounter++;
	}
	private volatile long drawingTimeSum	= 0;
	private volatile int drawingCounter	= 0;
	public int getDrawingCounterAndReset() {
		int val	= drawingCounter;
		drawingCounter	= 0;
		return val;
	}
	/**
	 * Retrieves time in nanos the component spent in drawing itself and resets
	 * the counter to zero.
	 * @return
	 */
	public long getDrawTimeSumAndReset() {
		long val	= drawingTimeSum;
		drawingTimeSum	= 0;
		return val;
	}



	/*Rectangle2D collumAzimut;
	@Override
	protected void paintAzimyt(Graphics arg0) {
		long drawStart	= System.nanoTime();
		Graphics2D g = (Graphics2D) arg0;
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		int w = chartWidth;
		int h = getHeight();
		g.setColor(Color.black);
		g.fillRect(0, 0, getWidth(), getHeight());

		g.drawImage(bufferedImages[drawIndex], chartXOffset, 0, w, h, null);

		if (displayMarker) {
			g.setColor(Color.gray);
			g.drawLine(displayMarkerX, 0, displayMarkerX, h);
			g.drawString(String.format("%.1fMHz", displayMarkerFrequency / 1000000.0), displayMarkerX + 5, h / 2);
		} //finish marker

		g.setColor(Color.white);
		if (stringBounds == null)
			stringBounds = g.getFontMetrics().getStringBounds("TEST", g);
		int fontHeight = (int) stringBounds.getHeight();
		int x = chartXOffset + w - 350;
		int y = h - fontHeight * (statusMessage.length + 1);
		g.drawString(renderingInfo, x, y);

		for (int i = 0; i < statusMessage.length; i++) {
			if (statusMessage[i] != null)
				g.drawString(statusMessage[i], x, y + fontHeight * (i + 1));
		}

		long drawingTime	= System.nanoTime()-drawStart;
		drawingTimeSum	+= drawingTime;
		drawingCounter++;
	}
*/


	// 2. Функция оценки яркости с учётом палитры
	public int getBrightnessWithPalette(int rgb, ColorPalette palette, int[] brightnessMap) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;

		// Ищем ближайший цвет в палитре
		int minDiff = Integer.MAX_VALUE;
		int bestIndex = 0;

		for (int i = 0; i < palette.size(); i++) {
			Color c = palette.getColor(i);
			int diff = Math.abs(r - c.getRed()) + Math.abs(g - c.getGreen()) + Math.abs(b - c.getBlue());

			if (diff < minDiff) {
				minDiff = diff;
				bestIndex = i;
			}
		}

		// Возвращаем соответствующую яркость из палитры
		return brightnessMap[bestIndex];
	}

	private int[] computePaletteBrightness(ColorPalette palette) {
		int[] brightnessMap = new int[palette.size()];
		for (int i = 0; i < palette.size(); i++) {
			Color c = palette.getColor(i);
			brightnessMap[i] = c.getRed() + c.getGreen() + c.getBlue();
		}
		return brightnessMap;
	}

	public void setIsCrossEnabled(boolean isCross) {
		this.isCross = isCross;
	}
}