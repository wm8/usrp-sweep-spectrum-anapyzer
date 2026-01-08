package usrp;

import com.sun.jna.Callback;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.FloatByReference;

public class UsrpSweepLibrary {

    public enum InitialReturnCode {
        OK(0, "Module loaded successfully"),
        NO_DEVICE(-1, "USRP device not found. Have you connected it?"),
        UNKNOWN_ERROR(-2, "Internal c++ error, need to fix library"),
        UNSUPPORTED_ERROR(-999999, "Unknown return code. Need to be supported in java code");

        private final int value;
        private String description;
        InitialReturnCode(int value, String description) {
            this.value = value;
            this.description = description;
        }
        public int getValue() {
            return value;
        }
        public String getDescription() {
            return description;
        }

        public static InitialReturnCode fromValue(int value) {
            for (InitialReturnCode errorCode : InitialReturnCode.values()) {
                if (errorCode.getValue() == value) {
                    return errorCode;
                }
            }
            return UNSUPPORTED_ERROR;
        }
    }

    public interface usrp_sweep_lib_start__fft_power_callback_callback extends Callback {
        void apply(byte full_sweep_done, int bins, DoubleByReference freqStart, float fft_bin_Hz, FloatByReference powerdBm);
    };


    public static native int usrp_sweep_lib_init();
    public static native int usrp_sweep_lib_start(
            usrp_sweep_lib_start__fft_power_callback_callback _fft_power_callback,
            double freq_min, double freq_max, int NFFT, double gain_db);
    public static native void usrp_sweep_lib_stop();
    public static native void usrp_sweep_lib_unload();
}
