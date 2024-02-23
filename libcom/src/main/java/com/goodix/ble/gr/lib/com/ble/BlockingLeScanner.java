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

package com.goodix.ble.gr.lib.com.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class BlockingLeScanner implements Comparator<BlockingLeScanner.Report> {
    public interface Filter {
        boolean matchLeScannerReport(BlockingLeScanner scanner, ScanResult result);
    }

    public static class Report {
        public final String mac;
        public final BluetoothDevice device;
        public int rssi;
        public int rssiAvg;
        public int rssiSum;
        public int rssiCnt;

        // ext info
        public boolean extended; // true - extended; false - legacy
        public int advertisingSetId; // 0xFF if no set id was is present.
        public int periodicAdvertisingInterval; // in units of 1.25ms. Valid range is 6 (7.5ms) to 65536 (81918.75ms). 0x00 means periodic advertising interval is not present.

        public Report(BluetoothDevice device) {
            this.device = device;
            this.mac = device.getAddress();
        }
    }

    public Context appCtx;

    private final BluetoothAdapter adapter;
    private final CbApi21 cb21;
    private BluetoothLeScanner leScanner = null;
    private Filter reportFilter = null;
    private int reportRssiFilter = -127;
    private final HashMap<String, Report> reportCache = new HashMap<>(128);
    private boolean scanning = false;
    private boolean abortWhenDiscoveredAnyOne = false;

    public BlockingLeScanner(Context appCtx) {
        this.appCtx = appCtx.getApplicationContext();

        this.cb21 = new CbApi21();
        adapter = BluetoothAdapter.getDefaultAdapter();
    }

    public List<Report> scan(long timeoutMilliseconds) throws Throwable {
        return scan(timeoutMilliseconds, null, false, null);
    }

    public synchronized List<Report> scan(long timeoutMilliseconds, Integer minRSSI, boolean abortIfFind, Filter filterCb) {
        if (timeoutMilliseconds < 1 || this.scanning) {
            return Collections.emptyList();
        }
        if (minRSSI != null) {
            this.reportRssiFilter = minRSSI;
        } else {
            this.reportRssiFilter = -127;
        }
        this.reportFilter = filterCb;
        this.reportCache.clear();
        this.scanning = false;
        this.abortWhenDiscoveredAnyOne = abortIfFind;
        leScanner = start(null);

        long stopTime = System.currentTimeMillis() + timeoutMilliseconds;
        try {
            while (this.scanning) {
                long now = System.currentTimeMillis();
                if (now < stopTime) {
                    this.wait(stopTime - now);
                    // in case of abortScan() in Filter.matchLeScannerReport()
                    Thread.sleep(10);
                } else {
                    break;
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        stop();

        if (reportCache.isEmpty()) {
            return Collections.emptyList();
        }

        final ArrayList<Report> reports = new ArrayList<>(reportCache.values());
        Collections.sort(reports, this);
        return reports;
    }

    @Nullable
    public Report scanForDevice(long timeoutMilliseconds, String mac) throws Throwable {
        final BluetoothDevice remoteDevice = adapter.getRemoteDevice(mac);
        return scanForDevice(timeoutMilliseconds, remoteDevice);
    }

    @Nullable
    public Report scanForDevice(long timeoutMilliseconds, BluetoothDevice device) throws Throwable {
        List<Report> reports = scan(timeoutMilliseconds, null, true, new Filter() {
            @Override
            public boolean matchLeScannerReport(BlockingLeScanner scanner, ScanResult result) {
                if (result.getDevice().equals(device)) {
                    return true;
                }
                return false;
            }
        });
        if (reports.isEmpty()) {
            return null;
        }
        return reports.get(0);
    }

    public synchronized void abortScan() {
        if (this.scanning) {
            this.scanning = false;
            //this.stop();
            this.notify();
        }
    }

    @SuppressLint("MissingPermission")
    private BluetoothLeScanner start(@Nullable BluetoothDevice device) {
        if (leScanner == null) {
            leScanner = adapter.getBluetoothLeScanner(); // 蓝牙关闭的时候，返回值为空
        }
        if (leScanner != null) {
            ScanSettings.Builder builder = new ScanSettings.Builder();
            builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setLegacy(false); // scan extended advertising
                if (adapter.isLeCodedPhySupported()) {
                    builder.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED);
                }
            }

            if (device != null) {
                final ArrayList<ScanFilter> scanFilters = new ArrayList<>(1);
                scanFilters.add(new ScanFilter.Builder().setDeviceAddress(device.getAddress()).build());
                leScanner.startScan(scanFilters, builder.build(), this.cb21);
            } else {
                leScanner.startScan(null, builder.build(), this.cb21);
            }
            this.scanning = true;
        } else {
            throw new IllegalStateException("leScanner = null");
        }
        return leScanner;
    }

    @SuppressLint("MissingPermission")
    private void stop() {
        if (leScanner != null) {
            leScanner.stopScan(this.cb21);
        }
    }

    @Override
    public int compare(Report o1, Report o2) {
        //return o1.rssiAvg - o2.rssiAvg;
        return o2.rssiAvg - o1.rssiAvg;
    }

    synchronized void pushReport(ScanResult result) {
        final BluetoothDevice device = result.getDevice();
        final String mac = device.getAddress();
        Report report = reportCache.get(mac);
        if (report == null) {
            report = new Report(device);
            reportCache.put(report.mac, report);
        }

        report.rssi = result.getRssi();
        report.rssiSum += report.rssi;
        report.rssiCnt++;
        report.rssiAvg = report.rssiSum / report.rssiCnt;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            report.extended = !result.isLegacy();
            report.advertisingSetId = result.getAdvertisingSid();
            report.periodicAdvertisingInterval = result.getPeriodicAdvertisingInterval();
        }

        if (this.abortWhenDiscoveredAnyOne && this.scanning) {
            this.scanning = false;
            //this.stop();
            this.notify();
        }
    }

    class CbApi21 extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                if (result.getRssi() < reportRssiFilter) {
                    return;
                }
                Filter filter = BlockingLeScanner.this.reportFilter;
                if (filter == null || filter.matchLeScannerReport(BlockingLeScanner.this, result)) {
                    pushReport(result);
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Filter filter = BlockingLeScanner.this.reportFilter;
            for (ScanResult result : results) {
                if (result.getRssi() < reportRssiFilter) {
                    continue;
                }
                if (filter == null || filter.matchLeScannerReport(BlockingLeScanner.this, result)) {
                    pushReport(result);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            String err;
            switch (errorCode) {
                case SCAN_FAILED_ALREADY_STARTED:
                    err = "Fails to start scan as BLE scan with the same settings is already started by the app.";
                    break;
                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    err = "Fails to start scan as app cannot be registered.";
                    break;
                case SCAN_FAILED_INTERNAL_ERROR:
                    err = "Fails to start scan due an internal error.";
                    break;
                case SCAN_FAILED_FEATURE_UNSUPPORTED:
                    err = "Fails to start power optimized scan as this feature is not supported.";
                    break;
                default:
                    err = "UNKNOWN(" + errorCode + ")";
            }
            Log.e("BlockingLeScanner", "onScanFailed: " + err);
            abortScan();
        }
    }
}
