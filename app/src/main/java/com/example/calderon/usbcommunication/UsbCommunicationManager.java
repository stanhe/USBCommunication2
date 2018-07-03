package com.example.calderon.usbcommunication;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileStreamFactory;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by calderon on 13.02.2018.
 */

public class UsbCommunicationManager {

    public static FileSystem currentFileSystem;
    private AppCompatActivity activity;
    private UsbMassStorageDevice[] usbMassStorageDevices;

    public UsbCommunicationManager(AppCompatActivity activity) {
        this.activity = activity;
        if (currentFileSystem == null) {
            discoverDevice();
        }
    }

    public void discoverDevice() {
        UsbManager usbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        usbMassStorageDevices = UsbMassStorageDevice.getMassStorageDevices(activity);

        if(usbMassStorageDevices.length == 0) {
            MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " No Usb Mass Storage Devices found\n");
            return;
        }
        MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " " + usbMassStorageDevices.length + " mass storage devices found:\n");
        for (UsbMassStorageDevice device : usbMassStorageDevices) {
            MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " " + device.getUsbDevice().getProductName() + " (" + device.getUsbDevice().getDeviceName() + ")\n");
        }

        UsbDevice usbDevice = (UsbDevice) activity.getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (usbDevice != null && usbManager.hasPermission(usbDevice)) {
            MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " Received Usb device via intent\n");
            setupDevice();
        }
        else {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(activity, 0, new Intent("com.example.calderon.usbcommunication.USB_PERMISSION"), 0);
            usbManager.requestPermission(usbMassStorageDevices[0].getUsbDevice(), permissionIntent);
        }
    }

    public void setupDevice() {
        MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " Try to setup device \"" + usbMassStorageDevices[0].getUsbDevice().getProductName() + "\"\n");
        try {
            usbMassStorageDevices[0].init();
            currentFileSystem = usbMassStorageDevices[0].getPartitions().get(0).getFileSystem();
            MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " Finished settings up device \"" + usbMassStorageDevices[0].getUsbDevice().getProductName() + "\"\n");
            MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " Device capacity: " + currentFileSystem.getCapacity() + "\n");
        }
        catch (Exception e) {
            MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " Settings up device failed!\n");
            MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " Exception: " + e.getMessage() + "\n");
        }
    }

    public void close(){
        usbMassStorageDevices[0].close();
    }

    public void deleteFile() {
        MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " start deleted file! \n");
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                try {
                    UsbFile root = currentFileSystem.getRootDirectory();
                        for (UsbFile usbFile : root.listFiles()) {
                            //MainActivity.logTextView.append(System.currentTimeMillis()/1000 + "  file: "+usbFile.getName() +"\n");
                            if (usbFile.getName().contains("book")) {
                                for (UsbFile bookChild : usbFile.listFiles()){
                                    if (bookChild.getName().contains("05381") || (bookChild.getName().contains("05425"))) {
                                        String name = bookChild.getName();
                                        bookChild.delete();
                                        MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " deleted file: "+name +"\n");
                                    }
                                }
                            }
                        }
                } catch (Exception e) {
                    e.printStackTrace();
                    MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " deleted file error: "+e.getMessage()+"\n");
                }
                MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " finish delete file!\n ");
            }
        });
    }


    public void copyFileToUsb(File file) {
        CopyToUsbTaskParam param = new CopyToUsbTaskParam();
        param.paramList = new ArrayList<>();
        CopyToUsbTaskParam.SingleToUsbParam propertiesParam = param.new SingleToUsbParam();
        propertiesParam.from = Uri.fromFile(file);
        param.paramList.add(propertiesParam);
        MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " Copying file from internal storage to usb device\n");
        CopyToUsbTask ct = new CopyToUsbTask();
        ct.execute(param);
    }
     public void copyFileToUsbWithName(File file,String name) {
        CopyToUsbTaskParam param = new CopyToUsbTaskParam();
        param.paramList = new ArrayList<>();
        CopyToUsbTaskParam.SingleToUsbParam propertiesParam = param.new SingleToUsbParam();
        propertiesParam.from = Uri.fromFile(file);
        propertiesParam.name = name;
        param.paramList.add(propertiesParam);
        MainActivity.logTextView.append(System.currentTimeMillis()/1000 + " Copying file from internal storage to usb device\n");
        CopyToUsbTask ct = new CopyToUsbTask();
        ct.execute(param);
    }


    private class CopyToUsbTaskParam {
        List<SingleToUsbParam> paramList;

        class SingleToUsbParam {
            Uri from;
            String name;
        }
    }

    private class CopyToUsbTask extends AsyncTask<CopyToUsbTaskParam, Integer, Void> {

        private List<CopyToUsbTaskParam.SingleToUsbParam> paramList;
        private CopyToUsbTaskParam.SingleToUsbParam param;
        private List<String> logs = new ArrayList<>();

        @Override
        protected Void doInBackground(CopyToUsbTaskParam... params) {
            if (currentFileSystem == null) {
                logs.add(System.currentTimeMillis()/1000 + " Usb Device was not properly set up! Cannot copy file");
                return null;
            }
            paramList = params[0].paramList;
            while(!paramList.isEmpty() ){
                param = paramList.remove(0);

                try {
                    UsbFile root = currentFileSystem.getRootDirectory();
                    UsbFile dirTo = null;
                    for(UsbFile usbFile : root.listFiles()){
                        if(usbFile.getName().endsWith("book")){
                            logs.add(System.currentTimeMillis()/1000 + " Folder usbCommunication found in usb device\n");
                            dirTo = usbFile;
                        }
                    }
                    if(dirTo == null) {
                        logs.add(System.currentTimeMillis()/1000 + " Creating folder usbCommunication in usb device\n");
                        UsbFile directory = root.createDirectory("book");
                        dirTo = directory;
                    }
/*                    int counter = 0;
                    for(UsbFile usbFile : dirTo.listFiles()) {
                        counter = Integer.parseInt(usbFile.getName().substring(usbFile.getName().indexOf('_') + 1, usbFile.getName().indexOf('.')));
                    }
                    counter++;*/
                    //UsbFile file = dirTo.createFile("myCreatedFile_" + counter + ".txt");
                    UsbFile file;
                    /*if (param.name.endsWith("kii")) {
                        file = root.createFile(param.name);
                    } else {
                        file = dirTo.createFile(param.name);
                    }*/
                    file = dirTo.createFile(param.name);

                    InputStream inputStream = activity.getContentResolver().openInputStream(param.from);
                    OutputStream outputStream = UsbFileStreamFactory.createBufferedOutputStream(file, currentFileSystem);

                    byte[] bytes = new byte[currentFileSystem.getChunkSize()];
                    logs.add(System.currentTimeMillis()/1000 + " chunkSize : "+bytes.length+ "\n");
                    int count;

                    while ((count = inputStream.read(bytes)) != -1){
                        outputStream.write(bytes, 0, count);
                    }

                    outputStream.flush();
                    outputStream.close();
                    inputStream.close();
                    file.flush();
                    file.close();

                    if (param.name.endsWith("kii")) {
                        logs.add(System.currentTimeMillis()/1000 + " start move kii file to book \n");

                    }
                    logs.add(System.currentTimeMillis()/1000 + " Successfully copied file to usb device: " + "/" + dirTo.getName() + "/" + file.getName() + "\n");
                } catch (Exception e) {
                    logs.add(System.currentTimeMillis()/1000 + " Error copying file to usb device: " + e.getMessage() + "\n");
                }
            }return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            appendToTextView(logs);
        }
    }

    public void appendToTextView(List<String> logs) {
        for (String log : logs) {
            MainActivity.logTextView.append(log);
        }
    }
}
