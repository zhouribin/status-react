package im.status.ethereum.module.data;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * A very lightweight service storing the password in RAM.
 */
public class StatusDataService extends Service {

    private static final String TAG = "StatusDataService";

    public static final int STORE_PASSWORD = 0xAA;
    public static final int RETRIEVE_PASSWORD = 0xBB;


    private Messenger messenger;


    private static class MessageHandler extends Handler {

        private String password;

        @Override
        public void handleMessage(Message msg) {
            Log.d("IGORM", String.format("StatusDataService / handleMessage: %d %s", msg.what, msg.obj));
            switch (msg.what) {
                case STORE_PASSWORD:
                    Log.d("IGORM", "Storing password");

                    this.setPassword(msg.obj != null ? msg.obj.toString() : null);
                    break;
                case RETRIEVE_PASSWORD:
                    Log.d("IGORM", "Attempting to retrieve password");
                    this.replyTo(msg.replyTo, this.getPassword());
            }
            super.handleMessage(msg);
        }

        private void replyTo(Messenger messenger, String password) {
            if (messenger == null) {
                Log.w(TAG, "replyTo: messenger is null, ignoring");
                return;
            }

            try {
                Log.d("IGORM", String.format("Replying with password %s", password));
                messenger.send(Message.obtain(null, 0, password));
            } catch (RemoteException e) {
                Log.e(TAG, "Error while sending data to the client.", e);
            }
        }

        private void setPassword(String password) {
            this.password = password;
        }

        private String getPassword() {
            return this.password;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (this.messenger == null) {
            this.messenger = new Messenger(new MessageHandler());
        }
        return messenger.getBinder();
    }
 }
