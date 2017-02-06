package com.github.nkzawa.socketio.androidchat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * A chat fragment containing messages view and input form.
 */
public class MainFragment extends Fragment {

    private static final int REQUEST_LOGIN = 0;

    private static final int TYPING_TIMER_LENGTH = 600;

    private static final int REQUEST_ENABLE_BT = 1;

    private RecyclerView mMessagesView;
    private EditText mInputMessageView;
    private List<Message> mMessages = new ArrayList<Message>();
    private RecyclerView.Adapter mAdapter;
    private boolean mTyping = false;
    private Handler mTypingHandler = new Handler();
    private String mUsername;
    private Socket mSocket;

    private Boolean isConnected = true;

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    Map<String, String[]> map = new HashMap<String, String[]>();

    Map<String, float[]> beaconCoordinates = new HashMap<String, float[]>();

    private BluetoothLeScanner LeScanner;

    Map<String, String> TriangulationCoord = new HashMap<String, String>();

    Map<String, String[]> AverageDistance = new HashMap<String, String[]>();


    public MainFragment() {
        super();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mAdapter = new MessageAdapter(activity, mMessages);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        ChatApplication app = (ChatApplication) getActivity().getApplication();
        mSocket = app.getSocket();
        mSocket.on(Socket.EVENT_CONNECT,onConnect);
        mSocket.on(Socket.EVENT_DISCONNECT,onDisconnect);
        mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.on("new message", onNewMessage);
        mSocket.on("user joined", onUserJoined);
        mSocket.on("user left", onUserLeft);
        mSocket.on("typing", onTyping);
        mSocket.on("stop typing", onStopTyping);
        mSocket.connect();

        startSignIn();

            if (mBluetoothAdapter == null) {
        // Device does not support Bluetooth
        }
        else {
            if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        LeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

        }
        else {
            LeScanner.startScan(new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, final ScanResult result) {
                    super.onScanResult(callbackType, result);
                    int change = 0;
                    String data;
                    int TxPower;
                    String major = null;
                    Log.v("Name","" + result.getDevice());
                    Log.v("RSSI","" + result.getRssi());
                    Log.v("ScanRecord",result.getScanRecord().toString());
                    Log.v("Data","" + ByteArrayToString(result.getScanRecord().getManufacturerSpecificData(76)));
                    data = ByteArrayToString(result.getScanRecord().getManufacturerSpecificData(76));
                    if(data != null && data.substring(data.length() - 4).contains("-")) {
                        data = data.substring(data.length() - 8);
                        major = data.substring(0, 3);
                        TxPower = Integer.valueOf(data.substring(4, data.length() - 1));
                        int rssi = result.getRssi();
                        Log.v("TxPower",""  + TxPower);
                        Log.v("Major","" + major);
                        calculateDistance(major,TxPower,rssi);
                        TriangulationCoord.put(major,"" + map.get(major)[2]);
                        average(major,map.get(major)[2]);
                    }

                    if(map.keySet().size() == 6) {
                        Map<String, String[]> NewMap = new HashMap<String, String[]>();
                        getPosition(getBiggerDistance());
                        for(String key: map.keySet()) {
                            NewMap.put(key,map.get(key));
                        }
                        Log.e("map_size","" + map.size());
                        Log.e("map_keySet","" + map.keySet());
                        mSocket.emit("new message", getMinDistance(NewMap));
                        map.clear();
                        Log.v("Map","" + NewMap.keySet());
                    }
                    if(major != null && AverageDistance.size() > 0) {
                        if (Integer.parseInt(AverageDistance.get(major)[1]) >= 10) {
                            for (String key : AverageDistance.keySet()) {
                                Log.e("Average_distance_" + key, "" + (Double.parseDouble(AverageDistance.get(key)[0]) /
                                        Integer.parseInt(AverageDistance.get(key)[1])));
                            }
                            AverageDistance.clear();
                        }
                    }
                }
            });
        }

        float[][] coordinates = {{2,4}, {0,1}, {3,0}, {5,2}, {8,0}, {6,4}};

        beaconCoordinates.put("101",coordinates[0]);
        beaconCoordinates.put("102",coordinates[1]);
        beaconCoordinates.put("103",coordinates[2]);
        beaconCoordinates.put("104",coordinates[3]);
        beaconCoordinates.put("105",coordinates[4]);
        beaconCoordinates.put("106",coordinates[5]);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();

        mSocket.off(Socket.EVENT_CONNECT, onConnect);
        mSocket.off(Socket.EVENT_DISCONNECT, onDisconnect);
        mSocket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.off(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.off("new message", onNewMessage);
        mSocket.off("user joined", onUserJoined);
        mSocket.off("user left", onUserLeft);
        mSocket.off("typing", onTyping);
        mSocket.off("stop typing", onStopTyping);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMessagesView = (RecyclerView) view.findViewById(R.id.messages);
        mMessagesView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mMessagesView.setAdapter(mAdapter);

        mInputMessageView = (EditText) view.findViewById(R.id.message_input);
        mInputMessageView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, KeyEvent event) {
                if (id == R.id.send || id == EditorInfo.IME_NULL) {
                    attemptSend();
                    return true;
                }
                return false;
            }
        });
        mInputMessageView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (null == mUsername) return;
                if (!mSocket.connected()) return;

                if (!mTyping) {
                    mTyping = true;
                    mSocket.emit("typing");
                }

                mTypingHandler.removeCallbacks(onTypingTimeout);
                mTypingHandler.postDelayed(onTypingTimeout, TYPING_TIMER_LENGTH);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        ImageButton sendButton = (ImageButton) view.findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptSend();
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (Activity.RESULT_OK != resultCode) {
            getActivity().finish();
            return;
        }

        mUsername = data.getStringExtra("username");
        int numUsers = data.getIntExtra("numUsers", 1);

        addLog(getResources().getString(R.string.message_welcome));
        addParticipantsLog(numUsers);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_leave) {
            leave();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void addLog(String message) {
        mMessages.add(new Message.Builder(Message.TYPE_LOG)
                .message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addParticipantsLog(int numUsers) {
        addLog(getResources().getQuantityString(R.plurals.message_participants, numUsers, numUsers));
    }

    private void addMessage(String username, String message) {
        mMessages.add(new Message.Builder(Message.TYPE_MESSAGE)
                .username(username).message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addTyping(String username) {
        mMessages.add(new Message.Builder(Message.TYPE_ACTION)
                .username(username).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void removeTyping(String username) {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            Message message = mMessages.get(i);
            if (message.getType() == Message.TYPE_ACTION && message.getUsername().equals(username)) {
                mMessages.remove(i);
                mAdapter.notifyItemRemoved(i);
            }
        }
    }

    private void attemptSend() {
        if (null == mUsername) return;
        if (!mSocket.connected()) return;

        mTyping = false;

        String message = mInputMessageView.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            mInputMessageView.requestFocus();
            return;
        }

        mInputMessageView.setText("");
        addMessage(mUsername, message);

        // perform the sending message attempt.
        mSocket.emit("new message", message);
    }

    private void startSignIn() {
        mUsername = null;
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        startActivityForResult(intent, REQUEST_LOGIN);
    }

    private void leave() {
        mUsername = null;
        mSocket.disconnect();
        mSocket.connect();
        startSignIn();
    }

    private void scrollToBottom() {
        mMessagesView.scrollToPosition(mAdapter.getItemCount() - 1);
    }

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(!isConnected) {
                        if(null!=mUsername)
                            mSocket.emit("add user", mUsername);
                        Toast.makeText(getActivity().getApplicationContext(),
                                R.string.connect, Toast.LENGTH_LONG).show();
                        isConnected = true;
                    }
                }
            });
        }
    };

    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    isConnected = false;
                    Toast.makeText(getActivity().getApplicationContext(),
                            R.string.disconnect, Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity().getApplicationContext(),
                            R.string.error_connect, Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    String message;
                    try {
                        username = data.getString("username");
                        message = data.getString("message");
                    } catch (JSONException e) {
                        return;
                    }

                    removeTyping(username);
                    addMessage(username, message);
                }
            });
        }
    };

    private Emitter.Listener onUserJoined = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_joined, username));
                    addParticipantsLog(numUsers);
                }
            });
        }
    };

    private Emitter.Listener onUserLeft = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_left, username));
                    addParticipantsLog(numUsers);
                    removeTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        return;
                    }
                    addTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onStopTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        return;
                    }
                    removeTyping(username);
                }
            });
        }
    };

    private Runnable onTypingTimeout = new Runnable() {
        @Override
        public void run() {
            if (!mTyping) return;

            mTyping = false;
            mSocket.emit("stop typing");
        }
    };
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    Log.v("Premision","works");
                    LeScanner.startScan(new ScanCallback() {
                        @Override
                        public void onScanResult(int callbackType, final ScanResult result) {
                            super.onScanResult(callbackType, result);
                            Log.v("Name","" + result.getDevice());
                            Log.v("Name","" + result.getScanRecord().getDeviceName());
                            Log.v("Result","" + result.getScanRecord().getTxPowerLevel());
                        }
                    });

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public static String ByteArrayToString(byte[] ba)
    {
        if(ba != null) {
            StringBuilder hex = new StringBuilder(ba.length * 2);
            for (byte b : ba)
                hex.append(b + " ");

            return hex.toString();
        }

        return null;
    }

    public void calculateDistance(String major, int Tx, int rssi) {
        double distance = Math.pow(10d, ((double) (Tx - rssi )) / (10 * 2));
        Log.v("major",major);
        Log.v("Distance","" + (distance));
        String[] values = new String[3];
        values[0] = String.valueOf(Tx);
        values[1] = String.valueOf(rssi);
        values[2] = String.valueOf(distance);
        map.put(major,values);
    }

    public void average(String major, String distance) {
        String[] values;
        if(!AverageDistance.keySet().contains(major)) {
            values = new String[2];
            values[0] = distance;
            values[1] = "" + 1;
        }
        else {
            values = AverageDistance.get(major);
            values[0] = String.valueOf(Double.parseDouble(values[0]) + Double.parseDouble(distance));
            values[1] = String.valueOf(Integer.parseInt(values[1]) + 1);
        }
        AverageDistance.put(major,values);
    }

    public String getMinDistance(Map<String, String[]> map) {
        String min_name = null;
        double min_distance = 1000;
        String[] keys = new String[map.keySet().size()];
        int i = 0;
        for(String str: map.keySet()) {
            if(min_name == null) {
                min_name = str;
                min_distance = Double.parseDouble(map.get(str)[2]);
            }
            keys[i] = str;
            i++;
        }
        for(int j = 0; j < keys.length; j++) {
            if(Double.parseDouble(map.get(keys[j])[2]) < min_distance) {
                min_distance = Double.parseDouble(map.get(keys[j])[2]);
                min_name = keys[j];
            }
        }
        return min_name.substring(min_name.length() - 1);
    }
    
    public void getPosition(Map map) {
        Object[] keys = map.keySet().toArray();;
        float xa = beaconCoordinates.get(keys[0])[0];
        float ya = beaconCoordinates.get(keys[0])[1];
        float xb = beaconCoordinates.get(keys[1])[0];
        float yb = beaconCoordinates.get(keys[1])[1];
        float xc = beaconCoordinates.get(keys[2])[0];
        float yc = beaconCoordinates.get(keys[2])[1];
        float da = Float.parseFloat((String) map.get(keys[0]));
        float db = Float.parseFloat((String) map.get(keys[1]));
        float dc = Float.parseFloat((String) map.get(keys[2]));

        double yp = (Math.pow(da,2) - Math.pow(dc,2) - Math.pow(xa,2) + Math.pow(xc,2) - Math.pow(ya,2) + Math.pow(yc,2) + (Math.pow(db,2) - Math.pow(da,2) + Math.pow(xa,2) - Math.pow(xb,2) + Math.pow(ya,2) - Math.pow(yb,2))/(2*xa - 2*xb))/((ya - yb)/(xa - xb)*(2*xa - 2*xc) - 2*ya +2*yc);
        double xp = (Math.pow(db,2) - Math.pow(da,2) + Math.pow(xa,2) - Math.pow(xb,2) - Math.pow(ya,2) - Math.pow(yb,2) - yp*(2*ya -2*yb))/(2*xa - 2*xb);
        Log.e("X","" + xp);
        Log.e("Y","" + yp);

    }

    public Map<String, String> getBiggerDistance() {
        String max_name = null;
        String max_value = null;
        String[] keys = new String[TriangulationCoord.size()];
        int k = 0;
        Map<String, String> Coordinates = new HashMap<String, String>();
        Map<String, String> BiggerDistance = new HashMap<String, String>();
        for(String key: TriangulationCoord.keySet()) {
            Coordinates.put(key,TriangulationCoord.get(key));
            if(max_name == null && max_value == null) {
                max_name = key;
                max_value = TriangulationCoord.get(key);
            }
            keys[k] = key;
            k++;
        }
        for(int i = 0; i < 3; i++) {
            for(int j = 0; j < keys.length; j++) {
                if(Coordinates.get(keys[j]) != null && keys[j] != null) {
                    if (Double.parseDouble(Coordinates.get(keys[j])) > Double.parseDouble(max_value)) {
                        max_value = Coordinates.get(keys[j]);
                        max_name = keys[j];
                    }
                }
            }
            Coordinates.remove(max_name);
            keys = removeElements(keys,max_name);
            BiggerDistance.put(max_name,max_value);
            if(keys[0] != null) {
                max_name = keys[0];
                max_value = Coordinates.get(max_name);
            }
        }
        return BiggerDistance;
    }

    public static String[] removeElements(String[] input, String deleteMe) {
        String[] result = new String[input.length];
        int i = 0;

        for(String item : input)
            if(!deleteMe.equals(item)) {
                result[i] = item;
                i ++;
            }


        return result;
    }
}

