package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * compileOnly stub of the hidden framework interface. Never shipped; the real
 * interface is used at runtime via ShizukuBinderWrapper (wrapped, then handed to
 * the reflected PackageInstaller.Session constructor).
 */
public interface IPackageInstallerSession extends IInterface {

    abstract class Stub extends Binder implements IPackageInstallerSession {

        public static IPackageInstallerSession asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}
