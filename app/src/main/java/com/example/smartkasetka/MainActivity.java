package com.example.smartkasetka;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
        Button throwB, programB, sendMessageB, settingsB;
        TextView eventT;
        TextView pillsRemainT, pillsTotalT;
        static final int REQUEST_ENABLE_BT = 1;
        static final int SEND_SMS_PERMISSION_REQ=1;
        static final UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        BluetoothDevice bluetoothDevice = null;
        BluetoothSocket btSocket = null;
        String btAddress = null;
        OutputStream outputStream;
        InputStream inputStream;
        boolean connectionStatus = true;
        long time_hour;
        boolean addedTime = false;
        int pillsRemain;                //should be stored in a DB and updated with each pill throw
        final int pillsTotal = 0;      //max pills in dispenser
        char readSignal;                //incoming signal from esp32

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            throwB = (Button) findViewById(R.id.throwB);
            throwB = (Button) findViewById(R.id.programB);
            sendMessageB = (Button)findViewById(R.id.sendMessageB);
            sendMessageB.setEnabled(false);
            settingsB = (Button)findViewById(R.id.settingsB);
            eventT = (TextView) findViewById(R.id.eventT);
            eventT.setText("To throw pill press button on dispenser");
            pillsTotalT = (TextView) findViewById(R.id.pillsTotalT);
            pillsRemainT = (TextView) findViewById(R.id.pillsRemainT);
            pillsRemainT.setText(String.valueOf(pillsRemain));
            pillsTotalT.setText("/" + pillsTotal);

            //throw pill
            throwB.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alarmAlertDialog();
                }
            });

            //program dispenser
            programB.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    initializeBt();
                    connectBt();
                    writeOutput(1);
                    readInput();
                    }
            });

            if(checkPermission(Manifest.permission.SEND_SMS))
            {
                sendMessageB.setEnabled(true);
            }
            else
            {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, SEND_SMS_PERMISSION_REQ);
            }
            sendMessageB.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                String phoneNumber = "728070249";
                String smsMessage = "The person you're looking after has not taken their medicine";
                startSMSManager(phoneNumber, smsMessage);
                }
            });

            settingsB.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent myIntent = new Intent(MainActivity.this, SettingsActivity.class);

                    MainActivity.this.startActivity(myIntent);
                }
            });
        }

        @Override
        protected void onPause() {
            super.onPause();
            if (btSocket.isConnected()) {
                closeSocket();
            }
        }
        @Override
        protected void onResume() {
            super.onResume();
        }

        public void initializeBt() {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter == null) {
            // Device doesn't support Bluetooth
                Toast.makeText(getApplicationContext(),"Your Device doesn't support bluetooth.",Toast.LENGTH_SHORT).show();
                finish();
            }
            try {
                if (!btAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
            } catch (SecurityException e){e.printStackTrace();}
            Set<BluetoothDevice> btDevices = null;
            try {
                btDevices = btAdapter.getBondedDevices();
                Log.d("bonded", String.valueOf(btDevices));
                Log.d("containsesp", String.valueOf(btDevices.stream().anyMatch(bluetoothDevice -> {
                    return bluetoothDevice.getName().contains("ESP");
                })));
            } catch (SecurityException | NullPointerException e) {
                e.printStackTrace();
            }

            try {
            if (btDevices.isEmpty())
                Log.d("bonded", "empty");
            else {
                for (BluetoothDevice device : btDevices) {
                    if (device.getName().contains("ESP")) {
                    btAddress = device.getAddress();
                    Log.d("address", btAddress);
                    }
                }
            }

            bluetoothDevice = btAdapter.getRemoteDevice(btAddress);
            Log.d("getname", bluetoothDevice.getName());
            try {
                btSocket = bluetoothDevice.createRfcommSocketToServiceRecord(mUUID);
            } catch (SecurityException | IOException e) {
                connectionStatus = false;
                e.printStackTrace();
            }
            } catch (SecurityException | NullPointerException e) {
                connectionStatus = false;
                e.printStackTrace();
            }
            try {
                btAdapter.cancelDiscovery();
            } catch (SecurityException | NullPointerException e) {
                    e.printStackTrace();
            }
        }

        public void closeSocket() {
            try {
                btSocket.close();
                connectionStatus = false;
                Log.d("socketIsconnected", "false");
                Log.d("connectionstatus", String.valueOf(connectionStatus));
            } catch (NullPointerException | IOException e) {
                //connectionStatus = false;
                e.printStackTrace();
            }
        }

        public void connectBt() {
            try {
                btSocket.connect();
                connectionStatus = true;
                Log.d("socketIsConnected", String.valueOf(btSocket.isConnected()));
            } catch (SecurityException | IOException e) {
                e.printStackTrace();
                closeSocket();
            }
        }

        public void writeOutput(int signal) {
            if (btSocket.isConnected()) {
                try {
                    byte command = (byte) signal;
                    outputStream = btSocket.getOutputStream();
                    outputStream.write(command);
                    Log.d("socketwrite", String.valueOf(command));
                } catch (IOException e) {
                    e.printStackTrace();
                    connectionStatus = false;
                }
            }
        else eventT.setText("Not connected to device.");
        }

        public void readInput()
        {
            BluetoothConnection bluetoothConnection = new BluetoothConnection();
            Thread myThread = new Thread(bluetoothConnection);
            myThread.run();
        }

        public class BluetoothConnection implements Runnable {
            private boolean stopExecution = false;

            public void setStopExecution(boolean stopExecution) {
                this.stopExecution = stopExecution;
                closeSocket();
            }

            public void run() {
                while (!stopExecution) {
                    read();
                }
            }

            public void read() {
                try {
                    if (btSocket.isConnected()) {
                        connectionStatus = true;
                    }
                    else
                        eventT.setText("Not connected to device.");

                    inputStream = btSocket.getInputStream();
                    inputStream.skip(inputStream.available());
                } catch (IOException e) {
                    e.printStackTrace();
                    connectionStatus = false;
                    setStopExecution(true);
                }
                //boolean press_switch = false;
                if (connectionStatus /*&& !press_switch*/) {
                    try {
                        readSignal = (char) inputStream.read();
                        switch (readSignal) {
                            case '3': {
                                eventT.setText("Event: Pill thrown");                                //this event is never printed because view is not updated while being in loop
                                pillsRemain += 1;
                                pillsRemainT.setText(String.valueOf(pillsRemain));
                                setStopExecution(true);
                                break;
                            }
                            case '4': {
                                pillsRemain = 0;
                                pillsRemainT.setText(String.valueOf(pillsRemain));
                                eventT.setText("Event: Programming finished");
                                setStopExecution(true);
                                break;
                            }
                            case '5': {
                                eventT.setText("Event: Max position, program the dispenser");
                                setStopExecution(true);
                                break;
                            }
                            case '6': {
                                eventT.setText("Event: Press switch to throw pill");
                                //setStopExecution(true);
                                break;
                            }
                            default:
                                eventT.setText("Event: Wrong signal");
                                setStopExecution(true);
                                break;
                        }
                        Log.d("inputstream", String.valueOf(readSignal));
                    } catch (IOException e) {
                        e.printStackTrace();
                        setStopExecution(true);
                    }
                }
                }
            };

        //function invoked on scheduled alarm
        private void alarmAlertDialog(){
            //initialize alertdialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            //set title
            builder.setTitle(getResources().getString(R.string.app_name));
            //set message
            builder.setMessage("Time to take medication. Please press 'throw pill' and confirm by pressing button on the dispenser");
            //set non cancelable
            builder.setCancelable(false);

            //on pill throw
            builder.setPositiveButton("Throw pill", new DialogInterface.OnClickListener() {
                //throw pill
                @Override
                public void onClick(DialogInterface dialog, int which) {
                        if(pillsRemain>0) {
                            initializeBt();
                            connectBt();
                            if (btSocket.isConnected()) {
                                writeOutput(2);
                                readInput();
                            }
                        }
                        else eventT.setText("No pills. Program the dispenser");
                        //dismiss dialog
                    dialog.dismiss();
                }
            });

            //on throw pill later
            builder.setNegativeButton("Later", new DialogInterface.OnClickListener() {
            //throw later, add 1h
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    time_hour += 1;
                    //cancel alert dialog
                    eventT.setText("Alarm will repeat after 1h");
                    dialog.dismiss();
                }
            });
            builder.show();
        }

        /**
         * method to Send SMS
         *
         * Extras:
         *
         * "subject"
         *      A string for the message subject (usually for MMS only).
         * "sms_body"
         *      A string for the text message.
         *  EXTRA_STREAM
         *      A Uri pointing to the image or video to attach.
         *
         *  For More Info:
         *  https://developer.android.com/guide/components/intents-common#SendMessage
         *
         * @param phoneNumber on which SMS to send
         * @param message text Message to send with SMS
         */
        public void startSMSManager(String phoneNumber, String message){

                if(checkPermission(Manifest.permission.SEND_SMS))
                {
                SmsManager smsManager=SmsManager.getDefault();
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                }
                else {
                //ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, SEND_SMS_PERMISSION_REQ);
                Toast.makeText(MainActivity.this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
                }
        private boolean checkPermission(String sendSms) {
                int checkpermission = ContextCompat.checkSelfPermission(MainActivity.this,sendSms);
                return checkpermission == PackageManager.PERMISSION_GRANTED;
                }
        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                if (requestCode == SEND_SMS_PERMISSION_REQ) {
                    if (grantResults.length > 0 && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                        sendMessageB.setEnabled(true);
                    }
                }
        }
}