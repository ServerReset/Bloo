package android.os;

/** compileOnly stub of the framework type (no android.jar on a java-library classpath).
 *  Never shipped; :app resolves the real android.os.RemoteException on the bootclasspath.
 *  Extends Exception (a checked exception) like the real class. */
public class RemoteException extends Exception {
}
