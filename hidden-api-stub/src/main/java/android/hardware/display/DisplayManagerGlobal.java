package android.hardware.display;

import android.content.Context;

public class DisplayManagerGlobal {
    public static DisplayManagerGlobal getInstance() {
        throw new RuntimeException();
    }
    public VirtualDisplay createVirtualDisplayWrapper(VirtualDisplayConfig virtualDisplayConfig,
                                                      IVirtualDisplayCallback callbackWrapper, int displayId) {
        throw new RuntimeException("stub");
    }
    public VirtualDisplay createVirtualDisplayWrapper(VirtualDisplayConfig virtualDisplayConfig, Context windowContext,
                                                      IVirtualDisplayCallback callbackWrapper, int displayId) {
        throw new RuntimeException("stub");
    }
}
