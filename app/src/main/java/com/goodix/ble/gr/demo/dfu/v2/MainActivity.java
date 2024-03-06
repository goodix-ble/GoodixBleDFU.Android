/*
  *****************************************************************************************
  Copyright (c) 2019 GOODIX
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:
  * Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
  * Neither the name of GOODIX nor the names of its contributors may be used
  to endorse or promote products derived from this software without
  specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
  ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDERS AND CONTRIBUTORS BE
  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
  POSSIBILITY OF SUCH DAMAGE.
  *****************************************************************************************
  */

package com.goodix.ble.gr.demo.dfu.v2;

import android.bluetooth.BluetoothDevice;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.goodix.ble.gr.demo.dfu.v2.databinding.ActivityMainBinding;
import com.goodix.ble.gr.lib.dfu.v2.DfuProgressListener;
import com.goodix.ble.gr.lib.dfu.v2.EasyDfu2;
import com.goodix.ble.gr.lib.dfu.v2.fastdfu.FastDfu;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, FileSelector.ResultListener, BleScannerDlg.ScannerListener, DfuProgressListener {

    private ActivityMainBinding binding;
    private static final int ShowLogMaximumLastSize  = 2048;
    EasyDfu2 runningDfu1;
    EasyDfu2 runningDfu2;
    EasyDfu2 runningDfu3;
    FastDfu runningFastDfu1;
    FastDfu runningFastDfu2;
    FastDfu runningFastDfu3;
    int scanDeviceIndex = 1;
    BluetoothDevice device1 = null;
    BluetoothDevice device2 = null;
    BluetoothDevice device3 = null;
    private static  final boolean SupportMultiDevice = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        showVersionName();

        binding.scanBtn.setOnClickListener(this);
        binding.scanBtn2.setOnClickListener(this);
        binding.scanBtn3.setOnClickListener(this);

        if (!SupportMultiDevice){
            binding.device1Layout2.setVisibility(View.GONE);
            binding.device1Layout3.setVisibility(View.GONE);
        }

        binding.selectFileBtn.setOnClickListener(this);

        binding.startDfuBtn.setOnClickListener(this);
        binding.startDfuInCopyModeBtn.setOnClickListener(this);
        binding.startUpdateResourceBtn.setOnClickListener(this);
        binding.startDfuWithDfuBoot.setOnClickListener(this);
        binding.fastStartDfuBtn.setOnClickListener(this);
        binding.fastStartDfuInCopyModeBtn.setOnClickListener(this);
        binding.fastStartUpdateResourceBtn.setOnClickListener(this);

        binding.cancelBtn.setOnClickListener(this);
        binding.cancelBtn.setEnabled(false);

        binding.logEd.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            }
        });
    }

    void setUiEnabled(boolean enabled) {
        binding.scanBtn.setEnabled(enabled);
        binding.scanBtn2.setEnabled(enabled);
        binding.scanBtn3.setEnabled(enabled);

        binding.selectFileBtn.setEnabled(enabled);
        binding.extFlashCb.setEnabled(enabled);
        binding.addressEd.setEnabled(enabled);

        binding.startDfuBtn.setEnabled(enabled);
        binding.startDfuInCopyModeBtn.setEnabled(enabled);
        binding.startUpdateResourceBtn.setEnabled(enabled);
        binding.startDfuWithDfuBoot.setEnabled(enabled);
        binding.fastStartDfuBtn.setEnabled(enabled);
        binding.fastStartDfuInCopyModeBtn.setEnabled(enabled);
        binding.fastStartUpdateResourceBtn.setEnabled(enabled);

        binding.cancelBtn.setEnabled(!enabled);
    }

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
        @Override
        public void onActivityResult(Map<String, Boolean> result) {

        }
    });

    private final FileSelector fwSelector = new FileSelector(this);
    private final BleScannerDlg devSelector = new BleScannerDlg();

    @Override
    public void onClick(View v) {
        if (v == binding.scanBtn) {
            scanDeviceIndex = 1;
            devSelector.show(this, this);
        } else if (v == binding.scanBtn2) {
            scanDeviceIndex = 2;
            devSelector.show(this, this);
        } else if (v == binding.scanBtn3) {
            scanDeviceIndex = 3;
            devSelector.show(this, this);
        } else if (v == binding.selectFileBtn) {
            fwSelector.show(this);
        } else if (v == binding.cancelBtn) {
            if (runningDfu1 != null) {
                runningDfu1.cancel();
            }
            if (runningDfu2 != null) {
                runningDfu2.cancel();
            }
            if (runningDfu3 != null) {
                runningDfu3.cancel();
            }
            if (runningFastDfu1 != null) {
                runningFastDfu1.cancel();
            }
            if (runningFastDfu2 != null) {
                runningFastDfu2.cancel();
            }
            if (runningFastDfu3 != null) {
                runningFastDfu3.cancel();
            }
        } else {
            onTestBtnClicked(v);
        }
    }

    void onTestBtnClicked(View clickedBtn) {
        final InputStream inputStream1 = fwSelector.openInputStream();
        final InputStream inputStream2 = fwSelector.openInputStream();
        final InputStream inputStream3 = fwSelector.openInputStream();

        if (inputStream1 == null || inputStream2 == null || inputStream3 == null) {
            Toast.makeText(this, "Can't read file.", Toast.LENGTH_LONG).show();
            return;
        }

        //TestCase.fastMode = binding.fastModeCb.isChecked(); // Only available for AppBootloader solution (DFU V2)
        boolean fastMode = binding.fastModeCb.isChecked(); // Only available for AppBootloader solution (DFU V2)

        if (clickedBtn == binding.startDfuBtn) {
            this.runningDfu1 = TestCase.testDfu(this, device1, inputStream1, this, fastMode);
            this.runningDfu2 = TestCase.testDfu(this, device2, inputStream2, this, fastMode);
            this.runningDfu3 = TestCase.testDfu(this, device3, inputStream3, this, fastMode);
            return;
        }
        if (clickedBtn == binding.startDfuWithDfuBoot) {
            this.runningDfu1 = TestCase.testAppTemplateDfuAndDfuBoot(this, device1, inputStream1, this);
            this.runningDfu2 = TestCase.testAppTemplateDfuAndDfuBoot(this, device2, inputStream2, this);
            this.runningDfu3 = TestCase.testAppTemplateDfuAndDfuBoot(this, device3, inputStream3, this);

            return;
        }
        if (clickedBtn == binding.fastStartDfuBtn) {
            this.runningFastDfu1 = TestCase.testFastDfu(this, device1, inputStream1, this);
            this.runningFastDfu2 = TestCase.testFastDfu(this, device2, inputStream2, this);
            this.runningFastDfu3 = TestCase.testFastDfu(this, device3, inputStream3, this);
            return;
        }

        // following test cases require more parameters.
        final boolean useExtFlash = binding.extFlashCb.isChecked();
        final String addrStr = binding.addressEd.getText().toString();
        Integer addr = null;
        try {
            addr = Integer.parseInt(addrStr, 16);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        if (clickedBtn == binding.startDfuInCopyModeBtn) {
            this.runningDfu1 = TestCase.testDfuCopyMode(this, device1, inputStream1, addr, this, fastMode); // DFU V2 supports recommended address.
            this.runningDfu2 = TestCase.testDfuCopyMode(this, device2, inputStream2, addr, this, fastMode); // DFU V2 supports recommended address.
            this.runningDfu3 = TestCase.testDfuCopyMode(this, device3, inputStream3, addr, this, fastMode); // DFU V2 supports recommended address.
        }

        if (addr == null) {
            // following test cases require explicitly address.
            Toast.makeText(this, "Please input valid address.", Toast.LENGTH_LONG).show();
            return;
        }

        if (clickedBtn == binding.startUpdateResourceBtn) {
            this.runningDfu1 = TestCase.testDfuResource(this, device1, inputStream1, useExtFlash, addr, this, fastMode);
            this.runningDfu2 = TestCase.testDfuResource(this, device2, inputStream2, useExtFlash, addr, this, fastMode);
            this.runningDfu3 = TestCase.testDfuResource(this, device3, inputStream3, useExtFlash, addr, this, fastMode);
        }

        if (clickedBtn == binding.fastStartDfuInCopyModeBtn) {
            this.runningFastDfu1 = TestCase.testFastDfuCopyMode(this, device1, inputStream1, addr, this);
            this.runningFastDfu2 = TestCase.testFastDfuCopyMode(this, device2, inputStream2, addr, this);
            this.runningFastDfu3 = TestCase.testFastDfuCopyMode(this, device3, inputStream3, addr, this);
        }
        if (clickedBtn == binding.fastStartUpdateResourceBtn) {
            this.runningFastDfu1 = TestCase.testFastDfuResource(this, device1, inputStream1, useExtFlash, addr, this);
            this.runningFastDfu2 = TestCase.testFastDfuResource(this, device2, inputStream2, useExtFlash, addr, this);
            this.runningFastDfu3 = TestCase.testFastDfuResource(this, device3, inputStream3, useExtFlash, addr, this);
        }
    }

    @Override
    public void onFileSelected(FileSelector selector, Uri fileUri, String fileName) {
        if (binding != null) {
            binding.fileInfoTv.setText(fileName);
        }
    }

    @Override
    public void onBluetoothDeviceSelected(BleScannerDlg dlg, BluetoothDevice device) {
        if (binding != null) {
            if (checkContainsSameAddress(device.getAddress())){
                Toast.makeText(MainActivity.this, "Device has selected", Toast.LENGTH_LONG).show();
                return;
            }
            if (1 == scanDeviceIndex){
                device1 = device;
                binding.deviceInfoTv.setText(device.getAddress());
            }else if (2 == scanDeviceIndex){
                device2 = device;
                binding.deviceInfoTv2.setText(device.getAddress());
            }else if (3 == scanDeviceIndex){
                device3 = device;
                binding.deviceInfoTv3.setText(device.getAddress());
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Progress of DFU
    @Override
    public void onDfuStart() {
        setUiEnabled(false);
        binding.tipTv.setText("Started...");
        binding.errorInfoTv.setVisibility(View.GONE);
        binding.upgradeProgressPB.setProgress(0);
        binding.logEd.setText("waiting complete log...");
    }

    @Override
    public void onDfuProgress(int percent, int speed, String message) {
        binding.tipTv.setText(percent + "% " + (speed / 1024) + "KBps  " + message);
        binding.upgradeProgressPB.setProgress(percent);

        if (percent == 0 && speed == 0 && message != null && message.startsWith("Use recommended address")) {
            final int i = message.indexOf("0x");
            binding.addressEd.setText(message.substring(i + 2));
        }
    }

    @Override
    public void onDfuComplete() {
        binding.upgradeProgressPB.setProgress(100);
        setUiEnabled(true);

        /*only print the last ShowLogMaximumLastSize bytes */
        StringBuilder logText = TestCase.ramLogger.logBuffer;
        String visiableLogText = logText.toString();
        if (logText.length() > ShowLogMaximumLastSize){
            int showFromIndex = logText.length() - ShowLogMaximumLastSize;
            visiableLogText = logText.substring(showFromIndex, logText.length() - 1);
        }
        binding.logEd.setText(visiableLogText);
    }

    @Override
    public void onDfuError(String message, Error error) {
        setUiEnabled(true);
        final StringWriter sw = new StringWriter(1024);
        error.printStackTrace(new PrintWriter(sw));
        binding.tipTv.setText(message);
        binding.errorInfoTv.setText(sw.toString());
        binding.errorInfoTv.setVisibility(View.VISIBLE);
        binding.logEd.setText(TestCase.ramLogger.logBuffer);
    }

    private boolean checkContainsSameAddress(String address){
        if ((scanDeviceIndex != 1) && (null != device1) && (device1.getAddress().equals(address))){
            return true;
        }
        if ((scanDeviceIndex != 2) && (null != device2) && (device2.getAddress().equals(address))){
            return true;
        }
        if ((scanDeviceIndex != 3) && (null != device3) && (device3.getAddress().equals(address))){
            return true;
        }
        return false;
    }

    private void showVersionName() {
        setTitle("Goodix BLE DFU (V2.0.2)");
    }
}