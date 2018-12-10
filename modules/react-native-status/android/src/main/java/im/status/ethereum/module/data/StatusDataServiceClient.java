package im.status.ethereum.module.data;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class StatusDataServiceClient {

    public interface Callback {
        void onPasswordRetrieved(String password);
    }

    private final static String TAG = "StatusDataServiceClient";

    private final DataServiceConnection connection = new DataServiceConnection();


    /* the interface */


    public void storePassword(String password) {
        connection.storePassword(password);
    }

    public void retrievePassword(Callback callback) {
        connection.retrievePassword(callback);
    }


    /* implementation details */

    public ServiceConnection getServiceConnection() {
        return connection;
    }

    public static class MessageHandler extends Handler {

        private StatusDataServiceClient.Callback callback;

        MessageHandler(HandlerThread thread) {
            super(thread.getLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d("IGORM", String.format("Retrieving pwd (service): %s", msg.obj));
            String password = msg.obj == null ? "" : msg.obj.toString();
            callback.onPasswordRetrieved(password);
        }

        public void setCallback(StatusDataServiceClient.Callback callback) {
            this.callback = callback;
        }
    }


    public class DataServiceConnection implements ServiceConnection {

        public void storePassword(String password) {
            if (senderMessenger == null) {
                Log.w(TAG, "Service is not connected, can't store data");
                return;
            }

            Message message = Message.obtain(null, StatusDataService.STORE_PASSWORD, password);

            try {
                senderMessenger.send(message);
            } catch (RemoteException e) {
                Log.e(TAG, "Error while storing data", e);
            }

        }

        public void retrievePassword(Callback callback) {
            if (senderMessenger == null) {
                Log.w(TAG, "Service is not connected, can't store data");
                callback.onPasswordRetrieved("");
                return;
            }

            handler.setCallback(callback);

            Message message = Message.obtain(null, StatusDataService.RETRIEVE_PASSWORD);
            message.replyTo = receiverMessenger;

            try {
                senderMessenger.send(message);
            } catch (RemoteException e) {
                Log.e(TAG, "Error while retrieving data", e);
                callback.onPasswordRetrieved("");
            }
        }


        private final MessageHandler handler;

        private Messenger senderMessenger = null;
        private final Messenger receiverMessenger;

        public DataServiceConnection() {
            HandlerThread handlerThread = new HandlerThread("DataHandlerThread");
            handlerThread.start();
            // Incoming message handler. Calls to its binder are sequential!
            this.handler = new MessageHandler(handlerThread);
            this.receiverMessenger = new Messenger(handler);
        }

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            senderMessenger = new Messenger(binder);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            senderMessenger = null;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            senderMessenger = null;
        }
    }

}
