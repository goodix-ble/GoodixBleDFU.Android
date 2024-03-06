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
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.goodix.ble.gr.lib.com.DataProgressListener;
import com.goodix.ble.gr.lib.com.ILogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SuppressWarnings("unused")
@SuppressLint("MissingPermission")
public class BlockingBle {
    private final String TAG;
    public final static UUID CCCD_UUID = new UUID(0x00002902_0000_1000L, 0x8000_00805F9B34FBL);
    public final static int DFU_MAX_MTU_IN_ANDROID_SIDE = 247;
    public static void setup(Context ctx) {
        if (ctx != null && appCtx == null) {
            appCtx = ctx.getApplicationContext();
        }
    }

    @SuppressLint("StaticFieldLeak")
    public static Context appCtx = null;

    private ILogger logger = null;

    @NonNull
    public final BluetoothDevice targetDevice;
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;

    @Nullable
    private BluetoothGatt targetGatt;
    private int mtu = 23;
    private boolean connected = false;
    private int txPhy = 1; // BluetoothDevice.PHY_LE_1M;
    private int rxPhy = 1; // BluetoothDevice.PHY_LE_1M;

    // Data
    Throwable lastError;

    private final static int MAX_CTRL_EVENT_CNT = 16;
    private final ArrayBlockingQueue<CtrlEvt> bleEvtQueue = new ArrayBlockingQueue<>(MAX_CTRL_EVENT_CNT);
    private final ArrayBlockingQueue<CtrlEvt> bleEvtQueuePool = new ArrayBlockingQueue<>(MAX_CTRL_EVENT_CNT);

    private final HashMap<BluetoothGattCharacteristic, ChrNtfBuf> ntfBufferPool = new HashMap<>(4);

    private final static int DEFAULT_GATT_TIMEOUT = 31000;

    // state-machine for writing characteristic
    private byte[] writeChrTaskData = null;
    private int writeChrTaskCurtPos = 0;
    private int writeChrTaskEndPos = 0;
    private byte[] writeChrTaskSegmentBuffer = null;

    public BlockingBle(BluetoothDevice device) {
        if (device == null) {
            throw new RuntimeException("BlockingBle(null)");
        }
        this.targetDevice = device;
        if (appCtx != null) {
            bluetoothManager = (BluetoothManager) appCtx.getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
        } else {
            bluetoothManager = null;
            bluetoothAdapter = null;
            throw new RuntimeException("Please call BlockingBLE.setup() firstly.");
        }
        this.TAG = createTag();
        initEvtPool();
    }

    public BlockingBle(String targetDeviceMAC) {
        if (appCtx != null) {
            bluetoothManager = (BluetoothManager) appCtx.getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
            targetDevice = bluetoothAdapter.getRemoteDevice(targetDeviceMAC);
        } else {
            bluetoothManager = null;
            bluetoothAdapter = null;
            throw new RuntimeException("Please call BlockingBLE.setup() firstly.");
        }
        this.TAG = createTag();
        if (this.targetDevice == null) {
            throw new RuntimeException("BlockingBle.targetDevice == null.");
        }
        initEvtPool();
    }

    public void setLogger(ILogger logger) {
        this.logger = logger;
    }

    public ILogger getLogger() {
        return logger;
    }

    public boolean isConnected() {
        BluetoothGatt gatt = this.targetGatt;
        return gatt != null && bluetoothManager.getConnectionState(this.targetDevice, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED;
    }

    public void connect() throws Throwable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            connect(BluetoothDevice.PHY_LE_1M_MASK, DEFAULT_GATT_TIMEOUT);
        } else {
            connect(0, DEFAULT_GATT_TIMEOUT);
        }
    }

    public void connect(long timeout) throws Throwable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            connect(BluetoothDevice.PHY_LE_1M_MASK, timeout);
        } else {
            connect(0, timeout);
        }
    }

    public void connect(int preferredPhyMask, long timeout) throws Throwable {
        // already connected
        if (this.targetGatt != null &&
                connected &&
                bluetoothManager.getConnectionState(this.targetDevice, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED) {
            return;
        }

        synchronized (this) {

            BluetoothGatt gatt;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // A variant of connectGatt with Handled can't be used here.
                // Check https://github.com/NordicSemiconductor/Android-BLE-Library/issues/54
                gatt = this.targetDevice.connectGatt(appCtx, false, gattCallback,
                        BluetoothDevice.TRANSPORT_LE, preferredPhyMask/*, mHandler*/);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                gatt = this.targetDevice.connectGatt(appCtx, false, gattCallback,
                        BluetoothDevice.TRANSPORT_LE);
            } else {
                gatt = this.targetDevice.connectGatt(appCtx, false, gattCallback);
            }
            BluetoothGatt prvGatt = targetGatt;
            targetGatt = gatt;

            if (prvGatt != null) {
                if (logger != null) {
                    logger.w(TAG, "Disconnect and close prv gatt.");
                }
                prvGatt.disconnect();
                prvGatt.close();
            }

            if (gatt != null) {
                try {
                    long expiredTime = System.currentTimeMillis() + timeout;
                    boolean waitResponse = true;
                    Throwable error = null;
                    while (waitResponse) {
                        final CtrlEvt evt = bleEvtQueue.poll(2000, TimeUnit.MILLISECONDS);
                        if (evt == null) {
                            long now = System.currentTimeMillis();
                            if (now >= expiredTime) {
                                targetGatt = null;
                                if (logger != null) {
                                    logger.w(TAG, "Close current gatt, for timeout.");
                                }
                                gatt.close();
                                throw new TimeoutException("Timeout to establish connection with " + gatt.getDevice().getAddress());
                            }
                        } else {
                            if (evt.evtType == CtrlEvt.EVT_CONNECTION_STATE_CHANGED && evt.gatt == gatt) {
                                evt.handled = true;
                                if (evt.status == BluetoothGatt.GATT_SUCCESS) {
                                    if (evt.newConnectionState == BluetoothProfile.STATE_CONNECTED) {
                                        if (logger != null)
                                            logger.i(TAG, "Device connected: " + gatt.getDevice().getAddress());
                                    } else {
                                        error = new Error("Failed to establish connection");
                                    }
                                    waitResponse = false;
                                } else {
                                    Thread.sleep(200);
                                    // 连接过程中，出现错误时，只要没有超时就重试
                                    if (!gatt.connect()) {
                                        error = new Error("gatt.connect()==false! Failed to establish connection with " + gatt.getDevice().getAddress());
                                        waitResponse = false;
                                    }
                                }
                            }
                            recycleCtrlEvt(evt);
                        }
                    }
                    if (error != null) {
                        lastError = error;
                        targetGatt = null;
                        if (logger != null) {
                            logger.w(TAG, "Close current gatt, for error: " + error.getMessage());
                        }
                        gatt.close();
                        throw error;
                    }
                } catch (InterruptedException e) {
                    if (logger != null) {
                        logger.w(TAG, "Disconnect and close current gatt, for interruption.");
                    }
                    // 如果是中断了，就是停止连接，就取消连接，释放资源。
                    gatt.disconnect();
                    Thread.sleep(200);
                    targetGatt = null;
                    gatt.close();

                    throw e;
                }

                // Connected successfully
                this.mtu = 23;
            }
        }
    }

    public void disconnect() throws Throwable {
        // no connection
        final BluetoothGatt gatt = this.targetGatt;
        final int connectionState = bluetoothManager.getConnectionState(this.targetDevice, BluetoothProfile.GATT);
        boolean waitDisconnectedEvt = true;
        if (gatt == null) {
            return;
        }
        if (connectionState == BluetoothProfile.STATE_DISCONNECTED || connectionState == BluetoothProfile.STATE_DISCONNECTING) {
            if (logger != null) {
                logger.w(TAG, "disconnect while connection state: " + connectionState);
            }
            waitDisconnectedEvt = false;
        }

        synchronized (this) {

            gatt.disconnect();

            try {
                if (waitDisconnectedEvt) {
                    boolean waitResponse = true;
                    while (waitResponse) {
                        final CtrlEvt evt = bleEvtQueue.poll(DEFAULT_GATT_TIMEOUT, TimeUnit.MILLISECONDS);
                        if (evt == null) {
                            throw new TimeoutException("Timeout to disconnect " + gatt.getDevice().getAddress());
                        } else {
                            if (evt.evtType == CtrlEvt.EVT_CONNECTION_STATE_CHANGED && evt.gatt == gatt) {
                                evt.handled = true;
                                if (evt.newConnectionState == BluetoothProfile.STATE_DISCONNECTED) {
                                    if (logger != null)
                                        logger.i(TAG, "Device disconnected: " + gatt.getDevice().getAddress());
                                    waitResponse = false;
                                }
                            }
                            recycleCtrlEvt(evt);
                        }
                    }
                }
            } finally {
                if (logger != null) {
                    logger.w(TAG, "Close current gatt, after disconnect().");
                }
                // release resource
                gatt.close();
            }
            this.targetGatt = null;
        }
    }

    public void discoverServices() throws Throwable {
        final ILogger logger = this.logger;
        synchronized (this) {
            final BluetoothGatt gatt = this.targetGatt;
            // no connection
            if (gatt == null || bluetoothManager.getConnectionState(this.targetDevice, BluetoothProfile.GATT) != BluetoothProfile.STATE_CONNECTED) {
                lastError = new Error("Connection is not established. Failed to discover the services of " + this.targetDevice.getAddress());
                throw lastError;
            }

            boolean success = gatt.discoverServices();
            if (!success) {
                lastError = new Error("gatt.discoverServices()==false. Failed to discover the services of " + this.targetDevice.getAddress());
                throw lastError;
            } else {
                CtrlEvt evt = waitGattCtrlEvt(gatt, CtrlEvt.EVT_SERVICE_DISCOVERED, "Timeout to discover the services of ", "Connection is lost while discovering the services of ");

                if (evt.status == BluetoothGatt.GATT_SUCCESS) {
                    if (logger != null) {
                        logger.i(TAG, "Service discovered: " + gatt.getDevice().getAddress());

                        // enumerate ATT
                        final List<BluetoothGattService> services = gatt.getServices();
                        if (services != null) {
                            for (BluetoothGattService service : services) {
                                logger.v(TAG, "<S> " + service.getUuid().toString() + " ------- #" + service.getInstanceId());

                                final List<BluetoothGattCharacteristic> chrList = service.getCharacteristics();
                                if (chrList != null) {
                                    for (BluetoothGattCharacteristic chr : chrList) {
                                        final StringBuilder str = new StringBuilder(60);
                                        str.append("<S> <C> ").append(chr.getUuid().toString()).append(" --- #").append(chr.getInstanceId());
                                        if (chr.getInstanceId() < 10) {
                                            str.append("  ");
                                        } else {
                                            str.append(" ");
                                        }
                                        final int properties = chr.getProperties();
                                        final String propertyNA = "-";
                                        if ((properties & BluetoothGattCharacteristic.PROPERTY_BROADCAST) != 0) {
                                            str.append("B");
                                        } else {
                                            str.append(propertyNA);
                                        }
                                        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                            str.append("R");
                                        } else {
                                            str.append(propertyNA);
                                        }
                                        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                                            str.append("d");
                                        } else {
                                            str.append(propertyNA);
                                        }
                                        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                                            str.append("W");
                                        } else {
                                            str.append(propertyNA);
                                        }
                                        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                            str.append("N");
                                        } else {
                                            str.append(propertyNA);
                                        }
                                        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                                            str.append("I");
                                        } else {
                                            str.append(propertyNA);
                                        }
                                        if ((properties & BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0) {
                                            str.append("S");
                                        } else {
                                            str.append(propertyNA);
                                        }
                                        if ((properties & BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) != 0) {
                                            str.append("E");
                                        } else {
                                            str.append(propertyNA);
                                        }
                                        logger.v(TAG, str.toString());

                                        final List<BluetoothGattDescriptor> dscList = chr.getDescriptors();
                                        if (dscList != null) {
                                            for (BluetoothGattDescriptor dsc : dscList) {
                                                logger.v(TAG, "<S> <C> <D> " + chr.getUuid().toString() + " #" + chr.getInstanceId());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    lastError = new Error("Failed to discover the services of " + this.targetDevice.getAddress() + ", status=" + evt.status);
                    throw lastError;
                }
            }
        }
    }

    public void setMtu(int newMtu) throws Throwable {
        synchronized (this) {
            final BluetoothGatt gatt = this.targetGatt;
            // no connection
            if (gatt == null || bluetoothManager.getConnectionState(this.targetDevice, BluetoothProfile.GATT) != BluetoothProfile.STATE_CONNECTED) {
                lastError = new Error("Connection is not established. Failed to set the MTU of " + this.targetDevice.getAddress());
                throw lastError;
            }

            boolean success = gatt.requestMtu(newMtu);
            if (!success) {
                lastError = new Error("gatt.requestMtu(" + newMtu + ")==false. Failed to set the MTU of " + this.targetDevice.getAddress());
                throw lastError;
            } else {
                final CtrlEvt evt = waitGattCtrlEvt(gatt, CtrlEvt.EVT_MTU_EXCHANGED, "Timeout to set the MTU of ", "Connection is lost while exchanging MTU with ");
                this.mtu = evt.mtu;
            }
        }
    }

    private CtrlEvt waitGattCtrlEvt(BluetoothGatt gatt, int evtType, String msgOnTimeout, String msgOnLossConnection) throws Throwable {
        CtrlEvt evt = null;
        long expiredTime = System.currentTimeMillis() + DEFAULT_GATT_TIMEOUT;
        boolean waitResponse = true;
        Throwable error = null;
        while (waitResponse) {
            evt = bleEvtQueue.poll(2000, TimeUnit.MILLISECONDS);
            if (evt == null) {
                long now = System.currentTimeMillis();
                if (now >= expiredTime) {
                    error = new TimeoutException(msgOnTimeout + gatt.getDevice().getAddress());
                    waitResponse = false;
                }
            } else {
                if (evt.evtType == evtType && evt.gatt == gatt) {
                    evt.handled = true;
                    waitResponse = false;
                }
                recycleCtrlEvt(evt);
            }
            // loss connection.
            if (!connected) {
                error = new Error(msgOnLossConnection + this.targetDevice.getAddress());
                waitResponse = false;
            }
        }

        if (error != null) {
            lastError = error;
            throw error;
        }

        return evt;
    }

    //setInterval()

    public void enableNotification(BluetoothGattCharacteristic chr, boolean enabled) throws Throwable {
        if (chr == null) {
            throw new Error("enableNotification(null)");
        }
        synchronized (this) {
            final BluetoothGatt gatt = this.targetGatt;
            // no connection
            if (gatt == null || bluetoothManager.getConnectionState(this.targetDevice, BluetoothProfile.GATT) != BluetoothProfile.STATE_CONNECTED) {
                lastError = new Error("Connection is not established. Failed to configure the notification of " + chr.getUuid().toString());
                throw lastError;
            }

            boolean success = false;
            Throwable error = null;

            int properties = chr.getProperties();
            if (0 == (properties & (BluetoothGattCharacteristic.PROPERTY_INDICATE | BluetoothGattCharacteristic.PROPERTY_NOTIFY))) {
                lastError = new Error("Neither PROPERTY_INDICATE nor PROPERTY_NOTIFY were found in characteristic: " + chr.getUuid().toString());
                throw lastError;
            }

            if (!gatt.setCharacteristicNotification(chr, enabled)) {
                error = new Error("gatt.setCharacteristicNotification()==false");
            } else {
                BluetoothGattDescriptor cccd = chr.getDescriptor(CCCD_UUID);
                if (cccd == null) {
                    error = new Error("Not found CCCD in characteristic: " + chr.getUuid().toString());
                } else {
                    if (enabled) {
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                            cccd.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                        } else {
                            cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        }
                    } else {
                        cccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    }

                    if (writeDescriptorCompat(chr, cccd)) {
                        success = true;
                    } else {
                        error = new Error("gatt.writeDescriptor(cccd) = false.");
                    }
                }
            }

            if (success) {
                long expiredTime = System.currentTimeMillis() + DEFAULT_GATT_TIMEOUT;
                boolean waitResponse = true;
                while (waitResponse) {
                    final CtrlEvt evt = bleEvtQueue.poll(2000, TimeUnit.MILLISECONDS);
                    if (evt == null) {
                        long now = System.currentTimeMillis();
                        if (now >= expiredTime) {
                            error = new TimeoutException("Timeout to set the CCCD of " + chr.getUuid().toString());
                            waitResponse = false;
                        }
                    } else {
                        if (evt.evtType == CtrlEvt.EVT_DSC_WRITTEN && evt.gatt == gatt && evt.descriptor.getCharacteristic() == chr) {
                            evt.handled = true;
                            if (evt.status != BluetoothGatt.GATT_SUCCESS) {
                                error = new Error("Failed to set the CCCD of " + chr.getUuid().toString() + ", status: " + evt.status);
                            }
                            waitResponse = false;
                        }
                        recycleCtrlEvt(evt);
                    }
                    // loss connection.
                    if (!connected) {
                        error = new Error("Connection is lost while configuring  the CCCD of " + chr.getUuid().toString());
                        waitResponse = false;
                    }
                }
            }

            if (error != null) {
                lastError = error;
                throw error;
            }

            synchronized (ntfBufferPool) {
                if (enabled) {
                    ChrNtfBuf ntfBuf = ntfBufferPool.get(chr);
                    if (ntfBuf == null) {
                        ntfBuf = new ChrNtfBuf();
                        ntfBufferPool.put(chr, ntfBuf);
                    }
                } else {
                    ntfBufferPool.remove(chr);
                }
            }
        }
    }

    @NonNull
    public List<BluetoothGattService> queryServices(UUID uuid) {
        if (uuid == null) return Collections.emptyList();
        synchronized (this) {
            final BluetoothGatt gatt = targetGatt;
            if (gatt == null) {
                return Collections.emptyList();
            }
            List<BluetoothGattService> list = gatt.getServices();
            if (list != null) {
                List<BluetoothGattService> outList = new ArrayList<>(list.size());
                for (BluetoothGattService s : list) {
                    if (s.getUuid().equals(uuid)) {
                        outList.add(s);
                    }
                }
                return outList;
            } else {
                return Collections.emptyList();
            }
        }
    }

    @NonNull
    public List<BluetoothGattCharacteristic> queryCharacteristics(BluetoothGattService service, UUID uuid) {
        if (service == null || uuid == null) return Collections.emptyList();
        synchronized (this) {
            final List<BluetoothGattCharacteristic> list = service.getCharacteristics();
            if (list != null) {
                List<BluetoothGattCharacteristic> outList = new ArrayList<>(list.size());
                for (BluetoothGattCharacteristic c : list) {
                    if (c.getUuid().equals(uuid)) {
                        outList.add(c);
                    }
                }
                return outList;
            } else {
                return Collections.emptyList();
            }
        }
    }

    public BluetoothGattCharacteristic queryCharacteristic(BluetoothGattService service, UUID uuid) {
        if (uuid == null || service == null) return null;
        synchronized (this) {
            return service.getCharacteristic(uuid);
        }
    }

    public BluetoothGattCharacteristic acquireCharacteristic(UUID uuid) {
        if (uuid == null) {
            throw new Error("acquireCharacteristic(null)");
        }

        final BluetoothGatt gatt = this.targetGatt;
        if (gatt == null) {
            throw new Error("acquireCharacteristic(){gatt == null}");
        }

        BluetoothGattCharacteristic chr = null;
        synchronized (this) {
            final List<BluetoothGattService> services = gatt.getServices();
            if (services != null) {
                for (BluetoothGattService s : services) {
                    chr = s.getCharacteristic(uuid);
                    if (chr != null) {
                        break;
                    }
                }
            }
        }
        if (chr == null) {
            throw new Error("Not found characteristic: " + uuid);
        }
        return chr;
    }

    public BluetoothGattDescriptor queryDescriptor(BluetoothGattCharacteristic chr, UUID uuid) {
        if (uuid == null || chr == null) return null;
        synchronized (this) {
            return chr.getDescriptor(uuid);
        }
    }

    public void writeChrWithResponse(BluetoothGattCharacteristic chr, long timeout, byte[] dat, int offsetInDat, int writeSize, @Nullable DataProgressListener listener) throws Throwable {
        writeChr(chr, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT, timeout, dat, offsetInDat, writeSize, listener);
    }

    public void writeChrWithoutResponse(BluetoothGattCharacteristic chr, long timeout, byte[] dat, int offsetInDat, int writeSize, @Nullable DataProgressListener listener) throws Throwable {
        writeChr(chr, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE, timeout, dat, offsetInDat, writeSize, listener);
    }

    //特性读写 函数writeCharWithResponse
    private void writeChr(BluetoothGattCharacteristic chr, int writeType, long timeout, byte[] dat, int offsetInDat, int writeSize, DataProgressListener listener) throws Throwable {
        if (chr == null || dat == null) {
            return;
        }

        synchronized (this) {
            final BluetoothGatt gatt = this.targetGatt;
            // no connection
            if (gatt == null || bluetoothManager.getConnectionState(this.targetDevice, BluetoothProfile.GATT) != BluetoothProfile.STATE_CONNECTED) {
                lastError = new Error("Connection is not established. Failed to write " + chr.getUuid().toString());
                throw lastError;
            }

            int curPos = offsetInDat;
            int endPos = offsetInDat + writeSize;
            if (endPos < dat.length) {
                endPos = dat.length;
            }

            chr.setWriteType(writeType);

            long writeBeginTime = System.currentTimeMillis();
            long reportProgressTime = writeBeginTime;

            // trigger writing progress
            this.writeChrTaskData = dat;
            this.writeChrTaskCurtPos = curPos;
            this.writeChrTaskEndPos = endPos;
            //this.writeChrTaskFailed = false;
            // failed to send first segment
            if (!processWriteChrTask(gatt, chr)) {
                lastError = new Error("gatt.writeCharacteristic(" + chr.getUuid().toString() + ") == false, writeType=" + writeType);
                throw lastError;
            }

            while (true) {
                final CtrlEvt evt = waitEvtOfChr(gatt, chr, CtrlEvt.EVT_CHR_WRITTEN, timeout, "Timeout to write ", "Connection is lost while writing ");

                if (evt.status != BluetoothGatt.GATT_SUCCESS) {
                    lastError = new Error("Failed to write " + chr.getUuid().toString() + ", writeType=" + chr.getWriteType() + ", status: " + evt.status);
                    throw lastError;
                }

                if (!evt.writeChrResult) {
                    lastError = new Error("gatt.writeCharacteristic(" + chr.getUuid().toString() + ") == false, writeType=" + writeType);
                    throw lastError;
                }

                if (listener != null) {
                    long now = System.currentTimeMillis();
                    listener.onDataProcessed(dat, evt.writeChrCurPos - offsetInDat, writeSize, now - reportProgressTime, now - writeBeginTime);
                    reportProgressTime = now;
                }

                if (evt.writeChrCurPos >= evt.writeChrEndPos) {
                    // complete
                    break;
                }
            }
        }
    }

    @NonNull
    public byte[] readChr(BluetoothGattCharacteristic chr, long timeout) throws Throwable {
        if (chr == null) {
            lastError = new IllegalArgumentException("readChr(null)");
            throw lastError;
        }

        byte[] val = null;

        synchronized (this) {
            // no connection
            final BluetoothGatt gatt = this.targetGatt;
            if (gatt == null || bluetoothManager.getConnectionState(this.targetDevice, BluetoothProfile.GATT) != BluetoothProfile.STATE_CONNECTED) {
                lastError = new Error("Connection is not established. Failed to read " + chr.getUuid().toString());
                throw lastError;
            }

            Throwable error = null;

            final boolean success = gatt.readCharacteristic(chr);

            if (success) {
                final CtrlEvt evt = waitEvtOfChr(gatt, chr, CtrlEvt.EVT_CHR_READ, timeout, "Timeout to read ", "Connection is lost while reading ");

                if (evt.status == BluetoothGatt.GATT_SUCCESS) {
                    val = evt.valOfChr;
                } else {
                    error = new Error("Failed to read " + chr.getUuid().toString() + ", status=" + evt.status);
                }
            } else {
                error = new Error("gatt.readCharacteristic(" + chr.getUuid().toString() + ")==false");
            }

            if (error != null) {
                lastError = error;
                throw error;
            }

            return val;
        }
    }

    private CtrlEvt waitEvtOfChr(BluetoothGatt gatt, BluetoothGattCharacteristic chr, int evtType, long timeout, String msgOnTimeout, String msgOnDisconnect) throws Throwable {
        CtrlEvt evt = null;
        if (timeout < 1) {
            timeout = DEFAULT_GATT_TIMEOUT;
        }
        long expiredTime = System.currentTimeMillis() + timeout;
        Throwable error = null;
        boolean waitResponse = true;
        while (waitResponse) {
            evt = bleEvtQueue.poll(2000, TimeUnit.MILLISECONDS);
            if (evt == null) {
                long now = System.currentTimeMillis();
                if (now >= expiredTime) {
                    error = new TimeoutException(msgOnTimeout + chr.getUuid().toString());
                    waitResponse = false;
                }
            } else {
                if (evt.evtType == evtType && evt.gatt == gatt && evt.characteristic == chr) {
                    evt.handled = true;
                    waitResponse = false;
                }
                recycleCtrlEvt(evt);
            }
            // loss connection.
            if (!connected) {
                error = new Error(msgOnDisconnect + chr.getUuid().toString());
                waitResponse = false;
            }
        }

        if (error != null) {
            lastError = error;
            throw error;
        }

        return evt;
    }

    public int readNtf(BluetoothGattCharacteristic chr, long timeout, byte[] outBuf, int offsetInBuf, int readSize) throws Throwable {
        if (chr == null || outBuf == null) {
            return 0;
        }

        // no connection
        if (bluetoothManager.getConnectionState(this.targetDevice, BluetoothProfile.GATT) != BluetoothProfile.STATE_CONNECTED) {
            lastError = new Error("Connection is not established. Failed to read notification of " + chr.getUuid().toString());
            throw lastError;
        }

        ChrNtfBuf ntfBuf;
        synchronized (ntfBufferPool) {
            ntfBuf = ntfBufferPool.get(chr);
        }

        // enable CCCD automatically
        if (ntfBuf == null) {
            enableNotification(chr, true);
        }

        if (ntfBuf == null) {
            lastError = new Error("The notification is invalid: " + chr.getUuid().toString());
            throw lastError;
        }


        int curPos = offsetInBuf;
        int endPos = offsetInBuf + readSize;
        if (endPos > outBuf.length) {
            endPos = outBuf.length;
        }

        while (curPos < endPos) {
            int ret = ntfBuf.read(timeout, outBuf, curPos, endPos - curPos);
            if (ret == 0) {
                if (curPos == offsetInBuf) {
                    // read nothing and loss connection
                    if (!connected) {
                        lastError = new Error("Connection is lost while waiting notification of " + chr.getUuid().toString());
                        throw lastError;
                    }
                }
                break;
            }
            curPos += ret;
        }

        return curPos - offsetInBuf;
    }

    @Nullable
    public byte[] readNtf(BluetoothGattCharacteristic chr, long timeout) throws Throwable {
        if (chr == null) {
            return null;
        }

        // no connection
        if (bluetoothManager.getConnectionState(this.targetDevice, BluetoothProfile.GATT) != BluetoothProfile.STATE_CONNECTED) {
            lastError = new Error("Connection is not established. Failed to read notification of " + chr.getUuid().toString());
            throw lastError;
        }

        ChrNtfBuf ntfBuf;
        synchronized (ntfBufferPool) {
            ntfBuf = ntfBufferPool.get(chr);
        }

        // enable CCCD automatically
        if (ntfBuf == null) {
            enableNotification(chr, true);
        }

        if (ntfBuf == null) {
            lastError = new Error("The notification is invalid: " + chr.getUuid().toString());
            throw lastError;
        }

        return ntfBuf.readChunk(timeout);
    }

    //回调函数
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            if (logger != null)
                logger.d(TAG, "onPhyUpdate() called with: gatt = [" + gatt.getDevice().getAddress() + "], txPhy = [" + txPhy + "], rxPhy = [" + rxPhy + "], status = [" + status + "]");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                CtrlEvt evt = bleEvtQueuePool.poll();
                if (evt == null) {
                    evt = new CtrlEvt();
                }
                evt.handled = false;
                evt.evtType = CtrlEvt.EVT_PHY_UPDATED;
                evt.gatt = gatt;
                evt.status = status;
                evt.txPhy = txPhy;
                evt.rxPhy = rxPhy;
                bleEvtQueue.offer(evt);

                BlockingBle.this.txPhy = txPhy;
                BlockingBle.this.rxPhy = rxPhy;
            }
        }

        @Override
        public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            if (logger != null)
                logger.d(TAG, "onPhyRead() called with: gatt = [" + gatt.getDevice().getAddress() + "], txPhy = [" + txPhy + "], rxPhy = [" + rxPhy + "], status = [" + status + "]");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                CtrlEvt evt = bleEvtQueuePool.poll();
                if (evt == null) {
                    evt = new CtrlEvt();
                }
                evt.handled = false;
                evt.evtType = CtrlEvt.EVT_PHY_UPDATED;
                evt.gatt = gatt;
                evt.status = status;
                evt.txPhy = txPhy;
                evt.rxPhy = rxPhy;
                bleEvtQueue.offer(evt);

                BlockingBle.this.txPhy = txPhy;
                BlockingBle.this.rxPhy = rxPhy;
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (logger != null)
                logger.d(TAG, "onConnectionStateChange() called with: gatt = [" + gatt.getDevice().getAddress() + "], status = [" + status + "], newState = [" + newState + "]");

            connected = newState == BluetoothProfile.STATE_CONNECTED;

            CtrlEvt evt = bleEvtQueuePool.poll();
            if (evt == null) {
                evt = new CtrlEvt();
            }
            evt.handled = false;
            evt.evtType = CtrlEvt.EVT_CONNECTION_STATE_CHANGED;
            evt.gatt = gatt;
            evt.status = status;
            evt.newConnectionState = newState;
            bleEvtQueue.offer(evt);

            ArrayList<ChrNtfBuf> ntfBufList;
            synchronized (ntfBufferPool) {
                ntfBufList = new ArrayList<>(ntfBufferPool.values());
                ntfBufferPool.clear();
            }
            // clear reading buffer
            for (ChrNtfBuf ntfBuf : ntfBufList) {
                ntfBuf.clear();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (logger != null)
                logger.d(TAG, "onServicesDiscovered() called with: gatt = [" + gatt.getDevice().getAddress() + "], status = [" + status + "]");

            CtrlEvt evt = bleEvtQueuePool.poll();
            if (evt == null) {
                evt = new CtrlEvt();
            }
            evt.handled = false;
            evt.evtType = CtrlEvt.EVT_SERVICE_DISCOVERED;
            evt.gatt = gatt;
            evt.status = status;
            bleEvtQueue.offer(evt);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (logger != null)
                logger.d(TAG, "onCharacteristicRead() called with: gatt = [" + gatt.getDevice().getAddress() + "], characteristic = [" + characteristic.getUuid().toString() + "], status = [" + status + "]  [" + characteristic.getValue().length + "]" + dump(characteristic.getValue()));

            CtrlEvt evt = bleEvtQueuePool.poll();
            if (evt == null) {
                evt = new CtrlEvt();
            }
            evt.handled = false;
            evt.evtType = CtrlEvt.EVT_CHR_READ;
            evt.gatt = gatt;
            evt.status = status;
            evt.characteristic = characteristic;
            evt.valOfChr = characteristic.getValue();
            bleEvtQueue.offer(evt);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            final byte[] value = characteristic.getValue();
            final ILogger logger = BlockingBle.this.logger;

            // continue sending
            final int curPos = writeChrTaskCurtPos;
            final int endPos = writeChrTaskEndPos;
            boolean writeChrResult = true;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeChrResult = processWriteChrTask(gatt, characteristic);
            }

            // postpone printing log
            if (logger != null)
                logger.d(TAG, "onCharacteristicWrite() called with: gatt = [" + gatt.getDevice().getAddress() + "], characteristic = [" + characteristic.getUuid().toString() + "], status = [" + status + "]  [" + value.length + "]" + dump(value));

            // for updating progress
            CtrlEvt evt = bleEvtQueuePool.poll();
            if (evt == null) {
                evt = new CtrlEvt();
            }
            evt.handled = false;
            evt.evtType = CtrlEvt.EVT_CHR_WRITTEN;
            evt.gatt = gatt;
            evt.status = status;
            evt.characteristic = characteristic;
            evt.valOfChr = value;
            evt.writeChrCurPos = curPos;
            evt.writeChrEndPos = endPos;
            evt.writeChrResult = writeChrResult;
            bleEvtQueue.offer(evt);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            final ILogger logger = BlockingBle.this.logger;
            if (logger != null) {
                logger.d(TAG, "onCharacteristicChanged(): start: called with: gatt = [" + gatt.getDevice().getAddress() + "], characteristic = [" + characteristic.getUuid().toString() + "]  [" + characteristic.getValue().length + "]" + dump(value));
            }

            ChrNtfBuf datBuffer;
            synchronized (ntfBufferPool) {
                datBuffer = ntfBufferPool.get(characteristic);
            }
            if (datBuffer != null) {
                try {
                    datBuffer.write(characteristic.getValue());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (logger != null)
                logger.d(TAG, "onDescriptorRead() called with: gatt = [" + gatt.getDevice().getAddress() + "], descriptor = [" + descriptor.getUuid().toString() + "], status = [" + status + "] " + dump(descriptor.getValue()));

            CtrlEvt evt = bleEvtQueuePool.poll();
            if (evt == null) {
                evt = new CtrlEvt();
            }
            evt.handled = false;
            evt.evtType = CtrlEvt.EVT_DSC_READ;
            evt.gatt = gatt;
            evt.status = status;
            evt.descriptor = descriptor;
            evt.valOfDsc = descriptor.getValue();
            bleEvtQueue.offer(evt);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (logger != null)
                logger.d(TAG, "onDescriptorWrite() called with: gatt = [" + gatt.getDevice().getAddress() + "], descriptor = [" + descriptor.getUuid().toString() + "], status = [" + status + "] " + dump(descriptor.getValue()));

            CtrlEvt evt = bleEvtQueuePool.poll();
            if (evt == null) {
                evt = new CtrlEvt();
            }
            evt.handled = false;
            evt.evtType = CtrlEvt.EVT_DSC_WRITTEN;
            evt.gatt = gatt;
            evt.status = status;
            evt.descriptor = descriptor;
            evt.valOfDsc = descriptor.getValue();
            bleEvtQueue.offer(evt);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            if (logger != null)
                logger.d(TAG, "onReliableWriteCompleted() called with: gatt = [" + gatt.getDevice().getAddress() + "], status = [" + status + "]");
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (logger != null)
                logger.d(TAG, "onReadRemoteRssi() called with: gatt = [" + gatt.getDevice().getAddress() + "], rssi = [" + rssi + "], status = [" + status + "]");

            CtrlEvt evt = bleEvtQueuePool.poll();
            if (evt == null) {
                evt = new CtrlEvt();
            }
            evt.handled = false;
            evt.evtType = CtrlEvt.EVT_RSSI_READ;
            evt.gatt = gatt;
            evt.status = status;
            evt.rssi = rssi;
            bleEvtQueue.offer(evt);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (logger != null) {
                logger.d(TAG, "onMtuChanged() called with: gatt = [" + gatt.getDevice().getAddress() + "], mtu = [" + mtu + "], status = [" + status + "]");
            }

            if (mtu > DFU_MAX_MTU_IN_ANDROID_SIDE){
                mtu = DFU_MAX_MTU_IN_ANDROID_SIDE;
                if (logger != null) {
                    logger.d(TAG, "onMtuChanged() " + "mtu is too long for DFU, reset to " + mtu + " in android side.");
                }
            }

            CtrlEvt evt = bleEvtQueuePool.poll();
            if (evt == null) {
                evt = new CtrlEvt();
            }
            evt.handled = false;
            evt.evtType = CtrlEvt.EVT_MTU_EXCHANGED;
            evt.gatt = gatt;
            evt.status = status;
            evt.mtu = mtu;
            bleEvtQueue.offer(evt);
        }

        @Override
        public void onServiceChanged(@NonNull BluetoothGatt gatt) {
            if (logger != null)
                logger.d(TAG, "onServiceChanged() called with: gatt = [" + gatt.getDevice().getAddress() + "]");

            CtrlEvt evt = bleEvtQueuePool.poll();
            if (evt == null) {
                evt = new CtrlEvt();
            }
            evt.handled = false;
            evt.evtType = CtrlEvt.EVT_SERVICE_CHANGED;
            evt.gatt = gatt;
            evt.status = BluetoothGatt.GATT_SUCCESS;
            bleEvtQueue.offer(evt);
        }

        // This method is hidden in Android Oreo and Pie
        // @Override
        @SuppressWarnings("unused")
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Keep
        public void onConnectionUpdated(final BluetoothGatt gatt,
                                        final int interval,
                                        final int latency,
                                        final int timeout,
                                        final int status) {
            if (logger != null)
                logger.d(TAG, "onConnectionUpdated() called with: gatt = [" + gatt.getDevice().getAddress() + "], interval = [" + interval + "], latency = [" + latency + "], timeout = [" + timeout + "], status = [" + status + "]");
        }
    };

    boolean processWriteChrTask(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        final int startPos = writeChrTaskCurtPos;
        final int endPos = writeChrTaskEndPos;
        if (startPos < endPos) {
            final byte[] taskData = writeChrTaskData;
            byte[] segmentBuffer = writeChrTaskSegmentBuffer;
            int maxSegmentSize = mtu - 3;
            int segmentSize = endPos - startPos;
            if (segmentSize > maxSegmentSize) {
                segmentSize = maxSegmentSize;
            }
            if (segmentBuffer == null || segmentBuffer.length != segmentSize) {
                segmentBuffer = new byte[segmentSize];
                writeChrTaskSegmentBuffer = segmentBuffer;
            }
            System.arraycopy(taskData, startPos, segmentBuffer, 0, segmentSize);
            writeChrTaskCurtPos += segmentSize;
            characteristic.setValue(segmentBuffer);
            final boolean ret = gatt.writeCharacteristic(characteristic);
            final ILogger logger = this.logger;
            if (logger != null) {
                logger.i(TAG, "writeCharacteristic = [" + gatt.getDevice().getAddress() + "], characteristic = [" + characteristic.getUuid().toString() + "], ret = [" + ret + "]  [" + segmentBuffer.length + "]" + dump(segmentBuffer));
            }
            return ret;
        }
        return true;
    }

    //tool
    public static char[] HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static String dump(byte[] dat) {
        if (dat == null) return "null";
        return dump(dat, 0, dat.length);
    }

    public static String dump(byte[] dat, int offset, int size) {
        if (dat == null) return "null";
        if (dat.length == 0 || size < 1) return "[]";

        int startPos = offset;
        if (startPos < 0) {
            startPos = dat.length + startPos;
        }

        int endPos = startPos + size;
        if (endPos > dat.length) {
            endPos = dat.length;
        }

        StringBuilder sb = new StringBuilder((endPos - startPos) * 2);
        for (int i = startPos; i < endPos; i++) {
            byte b = dat[i];
            sb.append(HEX[(b >> 4) & 0xF]);
            sb.append(HEX[b & 0xF]);

        }
        return sb.toString();
    }

    static class ChrNtfBuf {
        static final byte[] SIGNAL_QUIT_QUEUE = new byte[4];

        final ArrayBlockingQueue<byte[]> buffer = new ArrayBlockingQueue<>(128);

        private byte[] remainingData = null;
        private int remainingSize = 0;

        private boolean clearing = false;

        public synchronized int read(long timeout, byte[] outBuf, int startPos, int readSize) throws InterruptedException {
            if (clearing) {
                return 0;
            }
            if (remainingSize < 1) {
                final byte[] dat;
                if (timeout > 0) {
                    dat = buffer.poll(timeout, TimeUnit.MILLISECONDS);
                } else {
                    dat = buffer.take();
                }
                if (dat == null) {
                    return 0;
                }
                if (dat == SIGNAL_QUIT_QUEUE) {
                    return 0;
                }
                this.remainingData = dat;
                this.remainingSize = dat.length;
            }

            int pos = remainingData.length - remainingSize;
            int readableSize = readSize;
            if (readableSize > remainingSize) {
                readableSize = remainingSize;
            }
            remainingSize -= readableSize;

            System.arraycopy(this.remainingData, pos, outBuf, startPos, readableSize);

            return readableSize;
        }

//        public synchronized int read(long timeout, byte[] outBuf, int offsetInBuf, int readSize) throws InterruptedException {
//            if (outBuf == null) {
//                return 0;
//            }
//
//            int curPos = offsetInBuf;
//            int endPos = offsetInBuf + readSize;
//            if (endPos > outBuf.length) {
//                endPos = outBuf.length;
//            }
//
//            while (curPos < endPos) {
//                int ret = readFromRemainingData(timeout, outBuf, curPos, endPos - curPos);
//                if (ret == 0) {
//                    break;
//                }
//                curPos += ret;
//            }
//
//            return curPos - offsetInBuf;
//        }

        @Nullable
        public synchronized byte[] readChunk(long timeout) throws Throwable {
            if (clearing) {
                return null;
            }
            if (remainingSize > 0) {
                int pos = remainingData.length - remainingSize;
                int readableSize = remainingSize;
                remainingSize = 0;
                byte[] tmp;
                if (pos == 0) {
                    tmp = this.remainingData;
                } else {
                    tmp = new byte[readableSize];
                    System.arraycopy(this.remainingData, pos, tmp, 0, readableSize);
                }
                return tmp;
            } else {
                final byte[] dat;
                if (timeout > 0) {
                    dat = buffer.poll(timeout, TimeUnit.MILLISECONDS);
                } else {
                    dat = buffer.take();
                }
                if (dat == SIGNAL_QUIT_QUEUE) {
                    return null;
                }
                return dat;
            }
        }

        void write(byte[] data) throws InterruptedException {
            if (data != null) {
                buffer.put(data);
            }
        }

        void clear() {
            clearing = true;
            // notify other thread to exit synchronized function
            try {
                write(ChrNtfBuf.SIGNAL_QUIT_QUEUE);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // clear
            synchronized (this) {
                this.remainingSize = 0;
                this.remainingData = null;
            }
            // clear
            buffer.clear();
            clearing = false;
        }
    }

    private boolean writeDescriptorCompat(BluetoothGattCharacteristic parentChr, BluetoothGattDescriptor descriptor) {
        final BluetoothGatt gatt = this.targetGatt;
        if (gatt == null || descriptor == null || !connected)
            return false;

        final int originalWriteType = parentChr.getWriteType();
        parentChr.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        final boolean result = gatt.writeDescriptor(descriptor);
        parentChr.setWriteType(originalWriteType);
        return result;
    }

    private String createTag() {
        final StringBuilder tagBuilder = new StringBuilder(3 + 2 * 6 + 5);
        tagBuilder.append("ble_");
        final String mac = targetDevice.getAddress();
        for (int i = 0; i < mac.length(); i++) {
            char ch = mac.charAt(i);
            if (ch != ':') {
                tagBuilder.append(ch);
            }
        }
        return tagBuilder.toString();
    }

    private void initEvtPool() {
        for (int i = 0; i < MAX_CTRL_EVENT_CNT; i++) {
            try {
                bleEvtQueuePool.put(new CtrlEvt());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void recycleCtrlEvt(CtrlEvt evt) {
        // do nothing.
        bleEvtQueuePool.offer(evt);
    }

    public static class CtrlEvt {
        private static final int EVT_CONNECTION_STATE_CHANGED = 0;
        private static final int EVT_MTU_EXCHANGED = 1;
        private static final int EVT_PHY_UPDATED = 2;
        private static final int EVT_RSSI_READ = 3;
        private static final int EVT_CI_UPDATED = 4;
        private static final int EVT_SERVICE_DISCOVERED = 5;
        private static final int EVT_SERVICE_CHANGED = 6;
        private static final int EVT_DSC_READ = 10;
        private static final int EVT_DSC_WRITTEN = 11;
        private static final int EVT_CHR_READ = 12;
        private static final int EVT_CHR_WRITTEN = 13;

        // ****************************************************************************************
        // COMMON Part
        int evtType;
        BluetoothGatt gatt;
        int status; // error code
        boolean handled;

        // ****************************************************************************************
        // Connection state
        int newConnectionState;

        // ****************************************************************************************
        // MTU
        int mtu;

        // ****************************************************************************************
        // PHY
        int txPhy;
        int rxPhy;

        // ****************************************************************************************
        // RSSI
        int rssi;

        // ****************************************************************************************
        // CI

        // ****************************************************************************************
        // Service Discovered

        // ****************************************************************************************
        // Service changed

        // ****************************************************************************************
        // Descriptor read and written
        BluetoothGattDescriptor descriptor;
        byte[] valOfDsc;

        // ****************************************************************************************
        // Characteristic read and written
        BluetoothGattCharacteristic characteristic;
        byte[] valOfChr;
        int writeChrCurPos;
        int writeChrEndPos;
        boolean writeChrResult;
    }
}


