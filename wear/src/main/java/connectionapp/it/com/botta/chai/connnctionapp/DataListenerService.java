package connectionapp.it.com.botta.chai.connnctionapp;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.charset.Charset;

/**
 * Created by Chai on 31/10/2015.
 */
public class DataListenerService extends WearableListenerService {
    private static final String IN_DATA_PATH = "/in/data";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equalsIgnoreCase(IN_DATA_PATH)) {
            String result = new String(messageEvent.getData(), Charset.forName("UTF-8"));
            sendResult(result);
        }
        super.onMessageReceived(messageEvent);
    }

    public void sendResult(String message) {
        Intent intent = new Intent("REQUEST_PROCESSED");
        if (message != null) {
            intent.putExtra("MESSAGE", message);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }
}
