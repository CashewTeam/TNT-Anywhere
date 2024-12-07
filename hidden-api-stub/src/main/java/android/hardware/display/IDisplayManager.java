package android.hardware.display;

import android.media.projection.IMediaProjection;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.view.DisplayInfo;

public interface IDisplayManager extends IInterface {
    abstract class Stub extends Binder implements IDisplayManager {
        public static IDisplayManager asInterface(IBinder obj)
        {
            throw new RuntimeException("Stub!");
        }
    }
    DisplayInfo getDisplayInfo(int displayId);
    int createVirtualDisplay(VirtualDisplayConfig config, IVirtualDisplayCallback callback, IMediaProjection mediaProjection, String packageName);
}
