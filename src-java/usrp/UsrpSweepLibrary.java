package usrp;

import com.sun.jna.Callback;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.FloatByReference;

public class UsrpSweepLibrary {
    public interface usrp_sweep_lib_start__fft_power_callback_callback extends Callback {
        void apply(byte full_sweep_done, int bins, DoubleByReference freqStart, float fft_bin_Hz, FloatByReference powerdBm);
    };

    public static native void usrp_sweep_lib_init();
    public static native int usrp_sweep_lib_start(usrp_sweep_lib_start__fft_power_callback_callback _fft_power_callback, int freq_min, int freq_max, int fft_bin_width, int num_samples, int lna_gain, int vga_gain, int _antennaPowerEnable, int _enableAntennaLNA, String serial_number);
    public static native void usrp_sweep_lib_stop();
    public static native void usrp_sweep_lib_unload();
}
