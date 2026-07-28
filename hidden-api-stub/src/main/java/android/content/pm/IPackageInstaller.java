package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

/**
 * compileOnly stub of the hidden framework interface. Only the members the install
 * path uses are declared (getMySessions is intentionally omitted, which also lets us
 * skip stubbing ParceledListSlice). Never shipped; the real interface is used at
 * runtime via ShizukuBinderWrapper.
 */
public interface IPackageInstaller extends IInterface {

    void abandonSession(int sessionId)
            throws RemoteException;

    IPackageInstallerSession openSession(int sessionId)
            throws RemoteException;

    abstract class Stub extends Binder implements IPackageInstaller {

        public static IPackageInstaller asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}
