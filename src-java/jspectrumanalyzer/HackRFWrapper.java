package jspectrumanalyzer;

public class HackRFWrapper {
    static {
        System.loadLibrary("hackrf_info");
    }
    public native void print_massage();

    public static void main(String[] args) {
        HackRFWrapper hackRFWrapper = new HackRFWrapper();
        hackRFWrapper.print_massage();
    }
}
