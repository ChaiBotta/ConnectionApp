package connectionapp.it.com.botta.chai.connnctionapp;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.ErrorDialogFragment;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

public class MainActivity extends WearableActivity {


    private enum CONNECTION_STATE {CONNECTED, SUSPENDED, LOST}

    private static final String STATE_RESOLVING_ERROR = "RESOLVING_ERROR";
    private static final int REQUEST_RESOLVING_ERROR = 1001;
    private static final String ERROR_FRAGMENT_TAG = "ERROR_DIALOG";
    private boolean mResolvingError = false;
    private static final String IN_DATA_PATH = "/in/data";


    private GoogleApiClient mApiClient;


    private Node remoteNode = null;
    private CONNECTION_STATE state;
    private DisConnectThread disConnectThread;
    private ConnectThread connectThread;
    private BroadcastReceiver receiver;

    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadUi();

        mApiClient = new GoogleApiClient.Builder(this).addApi(Wearable.API).addOnConnectionFailedListener(mFailListener).addConnectionCallbacks(mConnectListener).build();


        connectThread = new ConnectThread(mApiClient);
        disConnectThread = new DisConnectThread(mApiClient);

        setAmbientEnabled();

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String result = intent.getStringExtra("MESSAGE");
                if (result != null)
                    textView.setText("message: " + result);
            }
        };

    }


    public void startSendMessage(View v){
            String values = "Hi from Android Wear";
            sendMessage(IN_DATA_PATH, values);

    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
    }

    @Override
    public void onExitAmbient() {
        super.onExitAmbient();
    }

    private GoogleApiClient.ConnectionCallbacks mConnectListener = new GoogleApiClient.ConnectionCallbacks() {


        @Override
        public void onConnected(Bundle bundle) {
            state = CONNECTION_STATE.CONNECTED;
            Wearable.NodeApi.addListener(mApiClient, mNodeListener);
            textView.setText("Connected");
            if (connectThread != null) {
                try {
                    connectThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onConnectionSuspended(int i) {
            state = CONNECTION_STATE.SUSPENDED;
            textView.setText("Connection Lost");
        }
    };

    private GoogleApiClient.OnConnectionFailedListener mFailListener = new GoogleApiClient.OnConnectionFailedListener() {
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
                    if (connectThread != null) {
                        try {
                            connectThread.join();
                            connectThread = new ConnectThread(mApiClient);
                            connectThread.start();
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
                    if (connectThread != null) {
                        try {
                            connectThread.join();
                            connectThread = new ConnectThread(mApiClient);
                            connectThread.start();
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
            fetchRemoteNode();
        }

        @Override
        public void onPeerDisconnected(Node node) {
            state = CONNECTION_STATE.LOST;
            remoteNode = null;

        }
    };

    public void loadUi() {
        WatchViewStub wStub = (WatchViewStub) findViewById(R.id.watch_view_stub);

        wStub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                textView = (TextView) findViewById(R.id.text);

            }
        });


    }


    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter("REQUEST_PROCESSED"));
    }

    @Override
    public void onPause() {
        super.onPause();

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

    @Override
    public void onStart() {
        super.onStart();
        if (!mResolvingError) {
            connectThread.start();
        }
    }


    @Override
    protected void onStop() {
        if (!mResolvingError) {
            Wearable.NodeApi.removeListener(mApiClient, mNodeListener);
            disConnectThread.start();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    private void fetchRemoteNode() {
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
                    remoteNode = null;
                }
            }
        });
    }

    private class ConnectThread extends Thread {

        private GoogleApiClient mApiClient;

        public ConnectThread(GoogleApiClient client) {
            mApiClient = client;
        }

        @Override
        public void run() {
            super.run();
            mApiClient.connect();
        }
    }

    private class DisConnectThread extends Thread {

        private GoogleApiClient mApiClient;

        public DisConnectThread(GoogleApiClient client) {
            mApiClient = client;
        }

        @Override
        public void run() {
            super.run();
            mApiClient.disconnect();
        }
    }

}
