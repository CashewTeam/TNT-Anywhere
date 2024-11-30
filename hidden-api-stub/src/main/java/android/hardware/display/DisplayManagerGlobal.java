package android.hardware.display;

public class DisplayManagerGlobal {
    public static DisplayManagerGlobal getInstance() {
        throw new RuntimeException();
    }
    public VirtualDisplay createVirtualDisplayWrapper(VirtualDisplayConfig virtualDisplayConfig,
                                                      IVirtualDisplayCallback callbackWrapper, int displayId) {
        throw new RuntimeException("stub");
    }
}
