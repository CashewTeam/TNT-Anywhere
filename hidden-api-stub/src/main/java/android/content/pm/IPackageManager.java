package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

public interface IPackageManager extends IInterface {
    abstract class Stub extends Binder implements IPackageManager {
        public static IPackageManager asInterface(IBinder obj)
        {
            throw new RuntimeException("Stub!");
        }
    }
    void grantRuntimePermission(String packageName, String permissionName, int userId);
}
