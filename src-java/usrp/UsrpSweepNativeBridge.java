package usrp;

import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.FloatByReference;
import jspectrumanalyzer.nativebridge.SweepDataCallback;


public class UsrpSweepNativeBridge {

    public static final String			JNA_LIBRARY_NAME	= "usrp-sweep";
    public static NativeLibrary JNA_NATIVE_LIB;
    private static boolean initialized = false;

    public static void loadMinGW() {
        try {
            String dir = "C:\\projects\\usrp_reader\\cmake-build-defaulttoolchain";

            // 1) грузим DLL (как ты уже сделал)
            System.load(dir + "\\usrp-sweep.dll");

            // 2) делаем JNA binding: связываем native методы с экспортами DLL
            NativeLibrary lib = NativeLibrary.getInstance(dir + "\\usrp-sweep.dll");
            Native.register(UsrpSweepLibrary.class, lib);
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        }
        initialized = true;
    }

    public static synchronized boolean init() {
        if (initialized) {
            return true;
        }
        try {
            /**
             * to make sure unpacked jnidispatch.dll is properly loaded
             * jnidispatch.dll is used directly instead of JNA bundled jar, because it is much faster to load
             */
            String pathPrefix = "C:\\projects\\usrp_reader\\cmake-build-debug-visual-studio\\";// "./" + Platform.RESOURCE_PREFIX + "/";
            System.setProperty("jna.boot.library.path", pathPrefix);
            System.setProperty("jna.nosys", "true");
            /*Native.DEBUG_JNA_LOAD	= true;
            Native.DEBUG_LOAD	= true;*/

            NativeLibrary.addSearchPath(JNA_LIBRARY_NAME, pathPrefix);
            JNA_NATIVE_LIB = NativeLibrary.getInstance(JNA_LIBRARY_NAME);
            System.out.println("Trying to load: " + JNA_NATIVE_LIB.getFile());
            Native.register(UsrpSweepLibrary.class, JNA_NATIVE_LIB);
            System.out.println("Loaded: " + JNA_NATIVE_LIB.getFile());
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        initialized = true;
        return true;
    }

    public static synchronized void start(SweepDataCallback dataCallback, int freq_min_MHz, int freq_max_MHz, int fft_bin_width, int num_samples,
                                          int lna_gain, int vga_gain, boolean antennaPowerEnable, boolean internalLNA, String serial_number) {
        UsrpSweepLibrary.usrp_sweep_lib_start__fft_power_callback_callback callback = new UsrpSweepLibrary.usrp_sweep_lib_start__fft_power_callback_callback()
        {
            @Override public void apply(byte sweep_started, int bins, DoubleByReference freqStart, float fftBinWidth, FloatByReference powerdBm)
            {
                double[] freqStartArr = bins == 0 ? null : freqStart.getPointer().getDoubleArray(0, bins);
                float[] powerArr =  bins == 0 ? null : powerdBm.getPointer().getFloatArray(0, bins);
                dataCallback.newSpectrumData(sweep_started==0 ? false : true, freqStartArr, fftBinWidth, powerArr);
            }
        };
        Native.setCallbackThreadInitializer(callback, new CallbackThreadInitializer(true));
        int result = UsrpSweepLibrary.usrp_sweep_lib_start(callback, freq_min_MHz, freq_max_MHz, fft_bin_width, num_samples, lna_gain, vga_gain, antennaPowerEnable ? 1 : 0, internalLNA ? 1 : 0, serial_number);

        if (result != 0) {
            dataCallback.errorData(result);
        }
    }

    public static void stop()
    {
        UsrpSweepLibrary.usrp_sweep_lib_stop();
    }
}
