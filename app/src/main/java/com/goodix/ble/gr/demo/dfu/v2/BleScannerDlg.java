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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BleScannerDlg extends DialogFragment implements View.OnClickListener, Runnable {

    public interface ScannerListener {
        void onBluetoothDeviceSelected(BleScannerDlg dlg, BluetoothDevice device);
    }

    public BluetoothDevice selectedDevice;
    public ScannerListener listener;

    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
    BluetoothLeScanner leScanner;

    boolean scanning = false;
    boolean pendingUpdate = false;
    Handler uiHandler = new Handler(Looper.getMainLooper());
    final HashMap<String, ScanResult> resultPool = new HashMap<>(128);
    static final ArrayList<ScanResult> resultList = new ArrayList<>(128);

    LinearLayout rootView;
    RecyclerView recyclerView;
    Button scanBtn;
    ScanResultAdapter scanResultAdapter;

    public void show(FragmentActivity host, ScannerListener listener) {
        this.listener = listener;
        show(host.getSupportFragmentManager(), this.getClass().getSimpleName());
    }

    public void show(Fragment host, ScannerListener listener) {
        this.listener = listener;
        show(host.getChildFragmentManager(), this.getClass().getSimpleName());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final Context ctx = inflater.getContext();

        btManager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        if (btManager != null) {
            btAdapter = btManager.getAdapter();
            if (btAdapter != null) {
                leScanner = btAdapter.getBluetoothLeScanner();
            }
        }

        final DisplayMetrics displayMetrics = ctx.getResources().getDisplayMetrics();

        rootView = new LinearLayout(ctx);
        rootView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setGravity(Gravity.CENTER_HORIZONTAL);
        rootView.setPadding(40, 40, 40, 40);

        recyclerView = new RecyclerView(ctx);
        scanResultAdapter = new ScanResultAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(scanResultAdapter);
        rootView.addView(recyclerView, new LinearLayout.LayoutParams(displayMetrics.widthPixels * 8 / 10, displayMetrics.heightPixels * 6 / 10));

        scanBtn = new Button(ctx);
        final LinearLayout.LayoutParams lpScanBtn = new LinearLayout.LayoutParams(displayMetrics.widthPixels * 6 / 10, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpScanBtn.topMargin = 20;
        scanBtn.setLayoutParams(lpScanBtn);
        scanBtn.setTextColor(Color.WHITE);
        scanBtn.setOnClickListener(this);
        rootView.addView(scanBtn);

        setScanBtn(false);

        return rootView;
    }

    void setScanBtn(boolean scanning) {
        if (scanBtn != null) {
            if (scanning) {
                scanBtn.setText("STOP");
                scanBtn.setBackgroundColor(0xFFCC0000);
//                if (recyclerView != null) {
//                    recyclerView.setBackgroundColor(0xFFDDFFDD);
//                }
            } else {
                scanBtn.setText("SCAN");
                scanBtn.setBackgroundColor(0xFF00CC00);
//                if (recyclerView != null) {
//                    recyclerView.setBackgroundColor(Color.TRANSPARENT);
//                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (scanning) {
            stopScan();
        }
    }

    private void startScan() {
        if (leScanner == null) {
            leScanner = btAdapter.getBluetoothLeScanner();
        }

        final Context ctx = getContext();

        if (ctx != null) {
            final LocationManager locationManager = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                //final boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                final boolean net = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                if (!net) {
                    Toast.makeText(ctx, "Please turn on location service if there is no device found.", Toast.LENGTH_LONG).show();
                    if (leScanner == null) {
                        return;
                    }
                }
            }

            int cnt = 0;
            int mask = 0;
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                mask |= 0x01;
                cnt++;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    mask |= 0x02;
                    cnt++;
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    mask |= 0x04;
                    cnt++;
                }
                if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    mask |= 0x08;
                    cnt++;
                }
            }
            if (cnt > 0) {
                final String[] requestList = new String[cnt];
                cnt = 0;
                if ((mask & 0x01) != 0) {
                    requestList[cnt++] = Manifest.permission.ACCESS_COARSE_LOCATION;
                }
                if ((mask & 0x02) != 0) {
                    requestList[cnt++] = Manifest.permission.ACCESS_FINE_LOCATION;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if ((mask & 0x04) != 0) {
                        requestList[cnt++] = Manifest.permission.BLUETOOTH_SCAN;
                    }
                    if ((mask & 0x08) != 0) {
                        requestList[cnt] = Manifest.permission.BLUETOOTH_CONNECT;
                    }
                }
                multiPermissionLauncher.launch(requestList);
                return;
            }
        }

        if (btAdapter.getState() != BluetoothAdapter.STATE_ON) {
            activityResultLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }

        if (leScanner != null) {
            ScanSettings.Builder builder = new ScanSettings.Builder();
            builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setLegacy(false); // scan extended advertising
                if (btAdapter.isLeCodedPhySupported()) {
                    builder.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED);
                }
            }

            resultList.clear();
            resultPool.clear();
            leScanner.startScan(null, builder.build(), this.scanCallback);
            scanning = true;
            setScanBtn(true);
            if (scanResultAdapter != null) {
                scanResultAdapter.notifyDataSetChanged();
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        if (leScanner != null) {
            scanning = false;
            leScanner.stopScan(this.scanCallback);
            setScanBtn(false);
        }
    }

    final ActivityResultLauncher<String[]> multiPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
        @Override
        public void onActivityResult(Map<String, Boolean> result) {

            for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                if (!entry.getValue()) {
                    final Context ctx = getContext();
                    if (ctx != null) {
                        Toast.makeText(ctx, entry.getKey() + " is required.", Toast.LENGTH_LONG).show();
                    }
                    return;
                }
            }
            startScan();
        }
    });

    final ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
        @Override
        public void onActivityResult(ActivityResult result) {
            if (result.getResultCode() == Activity.RESULT_OK) {
                startScan();
            }
        }
    });

    final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            addReport(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                addReport(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            final String msg = "ScanCallback#onScanFailed(): errorCode = " + errorCode;
            Log.e("BleScannerDlg", msg);
            final Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
            }
        }
    };

    void addReport(ScanResult result) {
        if (!scanning) {
            return;
        }

        if (result.getRssi() > -65) {
            synchronized (resultPool) {
                resultPool.put(result.getDevice().getAddress(), result);
            }
        }

        if (!pendingUpdate) {
            pendingUpdate = true;
            uiHandler.postDelayed(this, 500);
        }
    }

    @Override
    public void onClick(View v) {
        if (scanning) {
            stopScan();
        } else {
            startScan();
        }
    }

    @Override
    public void run() {
        if (pendingUpdate) {
            pendingUpdate = false;
            synchronized (resultPool) {
                //synchronized (resultList) { only accessed in UI thread.
                resultList.clear();
                resultList.addAll(resultPool.values());
                //}
            }
            if (scanResultAdapter != null) {
                scanResultAdapter.notifyDataSetChanged();
            }
        }
    }

    class ScanResultAdapter extends RecyclerView.Adapter<VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(parent);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final ScanResult scanResult = resultList.get(position);
            final String mac = scanResult.getDevice().getAddress();
            holder.msg.setLength(0);
            holder.msg.append(mac)
                    .append(" ").append(scanResult.getRssi()).append("dbm")
                    .append("\n");
            if (scanResult.getScanRecord() != null) {
                String deviceName = scanResult.getScanRecord().getDeviceName();
                if (deviceName != null) {
                    holder.msg.append(deviceName);
                } else {
                    holder.msg.append("N/A");
                }
            }
            holder.infoTv.setText(holder.msg);
            holder.bindDevice = scanResult.getDevice();
        }

        @Override
        public int getItemCount() {
            return resultList.size();
        }
    }

    class VH extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView infoTv;
        final StringBuilder msg = new StringBuilder(64);
        BluetoothDevice bindDevice;

        public VH(@NonNull ViewGroup parent) {
            super(new TextView(parent.getContext()));
            infoTv = (TextView) itemView;
            infoTv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            //infoTv.setBackgroundColor(Color.CYAN);
            infoTv.setTextColor(Color.BLACK);
            infoTv.setTextSize(16.0f);
            infoTv.setLines(2);
            infoTv.setPadding(10, 30, 30, 10);

            infoTv.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (scanning) {
                stopScan();
            } else {
                dismiss();
                final BluetoothDevice device = this.bindDevice;
                final ScannerListener listener = BleScannerDlg.this.listener;
                if (device != null) {
                    BleScannerDlg.this.selectedDevice = device;
                    if (listener != null) {
                        listener.onBluetoothDeviceSelected(BleScannerDlg.this, device);
                        BleScannerDlg.this.listener = null;
                    }
                }
            }
        }
    }
}
