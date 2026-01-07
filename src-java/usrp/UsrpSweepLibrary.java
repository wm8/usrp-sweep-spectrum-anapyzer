package usrp;

import com.sun.jna.Callback;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.FloatByReference;

public class UsrpSweepLibrary {
    public interface usrp_sweep_lib_start__fft_power_callback_callback extends Callback {
        void apply(byte full_sweep_done, int bins, DoubleByReference freqStart, float fft_bin_Hz, FloatByReference powerdBm);
    };

    public static native void usrp_sweep_lib_init();
    public static native int usrp_sweep_lib_start(
            usrp_sweep_lib_start__fft_power_callback_callback _fft_power_callback,
            double freq_min, double freq_max, int NFFT, double gain_db);
    public static native void usrp_sweep_lib_stop();
    public static native void usrp_sweep_lib_unload();
}
