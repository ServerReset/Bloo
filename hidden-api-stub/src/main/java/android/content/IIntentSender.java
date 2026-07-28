package android.content;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;

/**
 * compileOnly stub of the hidden framework interface. Never shipped; a subclass of
 * Stub is passed to the reflected IntentSender(IIntentSender) constructor to receive
 * the install-session commit result. (The demo annotates the 7-arg overload with
 * @RequiresApi(26); dropped here so the stub needs no androidx.annotation dependency —
 * it's advisory only and doesn't affect override resolution.)
 */
public interface IIntentSender extends IInterface {

    int send(int code, Intent intent, String resolvedType,
             IIntentReceiver finishedReceiver, String requiredPermission, Bundle options);

    void send(int code, Intent intent, String resolvedType, IBinder whitelistToken,
              IIntentReceiver finishedReceiver, String requiredPermission, Bundle options);

    abstract class Stub extends Binder implements IIntentSender {

        public Stub() {
            throw new UnsupportedOperationException();
        }

        @Override
        public android.os.IBinder asBinder() {
            throw new UnsupportedOperationException();
        }

        public static IIntentSender asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}
