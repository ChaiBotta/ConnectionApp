package connectionapp.it.com.botta.chai.connnctionapp;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.ErrorDialogFragment;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.w3c.dom.Text;


public class MainActivity extends Activity {

    private enum CONNECTION_STATE { CONNECTED, SUSPENDED, LOST}

    private static final String STATE_RESOLVING_ERROR = "RESOLVING_ERROR";
    private static final int REQUEST_RESOLVING_ERROR = 1001;
    private static final String ERROR_FRAGMENT_TAG = "ERROR_DIALOG";
    private boolean mResolvingError = false;

    private static final String IN_DATA_PATH = "/in/data";

    private BroadcastReceiver receiver;

    private GoogleApiClient mApiClient;

    private CONNECTION_STATE state;

    private GoogleApiConnectThread connThread;

    private GoogleApiDisConnectThread disconnectThread;

    private Node remoteNode = null;
    private TextView textView;
    private EditText editText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = (TextView)findViewById(R.id.text);
        editText = (EditText)findViewById(R.id.editText);


        mApiClient = new GoogleApiClient.Builder(this).addApi(Wearable.API).addConnectionCallbacks(connCallback).addOnConnectionFailedListener(connFailCallback).build();
        mResolvingError = savedInstanceState != null && savedInstanceState.getBoolean(STATE_RESOLVING_ERROR);

        connThread = new GoogleApiConnectThread(mApiClient);
        disconnectThread = new GoogleApiDisConnectThread(mApiClient);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String result = intent.getStringExtra("MESSAGE");

                if (result != null)
                    textView.setText("message: " + result);
            }
        };

    }

    private void sendMessage(final String path, final String text) {
        if (state == CONNECTION_STATE.CONNECTED) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mApiClient).await();
                    for (Node node : nodes.getNodes()) {
                        Wearable.MessageApi.sendMessage(mApiClient, node.getId(), path, text.getBytes()).await();
                    }

                }
            }).start();
        }
    }

    public void startSendMessage(View v){
        String values = editText.getText().toString();
        if(values!=null){
            sendMessage(IN_DATA_PATH, values);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter("REQUEST_PROCESSED"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            connThread.start();
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

    }


    @Override
    protected void onStop() {
        if (!mResolvingError) {
            Wearable.NodeApi.removeListener(mApiClient, mNodeListener);
            disconnectThread.start();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    private GoogleApiClient.ConnectionCallbacks connCallback = new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(Bundle bundle) {
            state = CONNECTION_STATE.CONNECTED;
            Wearable.NodeApi.addListener(mApiClient, mNodeListener);

            if (connThread != null) {
                try {
                    connThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onConnectionSuspended(int i) {
            state = CONNECTION_STATE.SUSPENDED;

        }
    };

    private GoogleApiClient.OnConnectionFailedListener connFailCallback = new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {

            if (mResolvingError) {
                return;
            } else if (connectionResult.hasResolution()) {
                try {
                    mResolvingError = true;
                    connectionResult.startResolutionForResult(MainActivity.this, REQUEST_RESOLVING_ERROR);

                } catch (IntentSender.SendIntentException e) {
                    e.printStackTrace();
                    if (connThread != null) {
                        try {
                            connThread.join();
                            connThread = new GoogleApiConnectThread(mApiClient);
                            connThread.start();
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }

                    }
                }

            } else {
                showErrorDialog(connectionResult.getErrorCode());
                mResolvingError = true;
            }
        }
    };

    /**
     * @param errorCode the error code launched by the OnConnectionFailCallback
     */
    private void showErrorDialog(int errorCode) {
        Dialog errodDialog = GooglePlayServicesUtil.getErrorDialog(errorCode, this, REQUEST_RESOLVING_ERROR);
        ErrorDialogFragment dialogFragment = ErrorDialogFragment.newInstance(errodDialog, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                mResolvingError = false;
            }
        });

        dialogFragment.show(getFragmentManager(), ERROR_FRAGMENT_TAG);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_RESOLVING_ERROR) {
            mResolvingError = false;
            if (resultCode == RESULT_OK) {
                if (!mApiClient.isConnecting() && !mApiClient.isConnected()) {
                    if (connThread != null) {
                        try {
                            connThread.join();
                            connThread = new GoogleApiConnectThread(mApiClient);
                            connThread.start();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                    }
                }
            }
        }
    }

    private NodeApi.NodeListener mNodeListener = new NodeApi.NodeListener() {
        @Override
        public void onPeerConnected(Node node) {
            state = CONNECTION_STATE.CONNECTED;
            // fetchRemoteNode();
        }

        @Override
        public void onPeerDisconnected(Node node) {
            state = CONNECTION_STATE.LOST;
            remoteNode = null;
        }
    };


    private void fetchRemoteNode(final String path, final String data) {
        Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                // getNodes() return zero node or (ONLY) one node
                if (result.getNodes().size() > 0) {
                    state = CONNECTION_STATE.CONNECTED;
                    if (remoteNode == null)
                        remoteNode = result.getNodes().get(0);

                } else {
                    state = CONNECTION_STATE.LOST;
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_RESOLVING_ERROR, mResolvingError);
    }

    private class GoogleApiConnectThread extends Thread {

        private GoogleApiClient mApiClient;

        public GoogleApiConnectThread(GoogleApiClient client) {
            mApiClient = client;
        }

        @Override
        public void run() {
            super.run();
            mApiClient.connect();
        }
    }

    private class GoogleApiDisConnectThread extends Thread {

        private GoogleApiClient mApiClient;

        public GoogleApiDisConnectThread(GoogleApiClient client) {
            mApiClient = client;
        }

        @Override
        public void run() {
            super.run();
            mApiClient.disconnect();
        }
    }



}
