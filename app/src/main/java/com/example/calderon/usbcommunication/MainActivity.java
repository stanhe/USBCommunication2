package com.example.calderon.usbcommunication;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static com.example.calderon.usbcommunication.R.*;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_READWRITE_STORAGE = 10;
    private static final int REQUEST_EXT_STORAGE_WRITE_PERM = 110;
    private static String ACTION_USB_PERMISSION;
    public static TextView logTextView;

    private static UsbCommunicationManager usbCommunicationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.activity_main);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        ACTION_USB_PERMISSION = "com.example.calderon.usbcommunication.USB_PERMISSION";

        logTextView = (TextView) findViewById(id.logTextView);
        logTextView.setMovementMethod(new ScrollingMovementMethod());

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);

        Button createFileButton = (Button) findViewById(id.createButton);
        createFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                createNewFile();
            }
        });

        getPermissions();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
    }

    public void getPermissions() {
        int permissionCheck1 = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        int permissionCheck2 = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck1 != PackageManager.PERMISSION_GRANTED || permissionCheck2 != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_READWRITE_STORAGE);
        }
        else {
            logTextView.append(System.currentTimeMillis()/1000 + " Permission to access USB granted\n");
            continueAfterPermission();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_EXT_STORAGE_WRITE_PERM: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    logTextView.append(System.currentTimeMillis()/1000 + " Permission to access USB granted\n");
                }
                else {
                    logTextView.append(System.currentTimeMillis()/1000 + " Permission to access USB denied\n");
                }
            }
            case REQUEST_READWRITE_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    logTextView.append(System.currentTimeMillis()/1000 + " Permission to access USB granted\n");
                    continueAfterPermission();
                }
            }
        }
    }

    public void continueAfterPermission() {
        usbCommunicationManager = new UsbCommunicationManager(this);
    }

    private final BroadcastReceiver usbReceiver = new UsbReceiver();

    public static class UsbReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    logTextView.append(System.currentTimeMillis()/1000 + " Usb extra permission granted\n");
                    if (usbDevice != null) {
                        logTextView.append(System.currentTimeMillis()/1000 + " Received Usb device \"" + usbDevice.getProductName() + "\" via intent\n");
                        usbCommunicationManager.setupDevice();
                    }
                }
            }
            else if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    usbCommunicationManager.discoverDevice();
                }

            }
        }
    }

    public void createNewFile() {
        EditText editText = (EditText) findViewById(R.id.text);
        String text = editText.getText().toString();
        editText.setText("");
        logTextView.append(System.currentTimeMillis()/1000 + " Creating new file with text " + text + "\n");
        String internalStorage = Environment.getExternalStorageDirectory().getAbsolutePath() + "/usbCommunication";
        File root = new File(internalStorage);
        if (!root.exists()) {
            root.mkdirs();
        }
        String fileName = "myCreatedFile.txt";
        File myNewFile = new File(internalStorage + "/" + fileName);
        try {
            logTextView.append(System.currentTimeMillis()/1000 + " Creating file " + fileName + " in internal storage\n");
            FileWriter fileWriter = new FileWriter(myNewFile);
            fileWriter.write(text);
            fileWriter.flush();
            fileWriter.close();
            logTextView.append(System.currentTimeMillis()/1000 + " Successfully created file " + myNewFile.getAbsolutePath() + "\n");
        } catch (IOException e) {
            logTextView.append(System.currentTimeMillis()/1000 + " Couldn't create file: " + e.getMessage() + "\n");
        }
        usbCommunicationManager.copyFileToUsb(myNewFile);
    }

}
