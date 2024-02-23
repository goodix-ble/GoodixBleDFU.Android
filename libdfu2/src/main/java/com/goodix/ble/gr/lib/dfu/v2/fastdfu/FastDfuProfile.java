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

package com.goodix.ble.gr.lib.dfu.v2.fastdfu;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import com.goodix.ble.gr.lib.com.DataProgressListener;
import com.goodix.ble.gr.lib.com.HexSerializer;
import com.goodix.ble.gr.lib.com.ble.BlockingBle;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class FastDfuProfile {
    public final static UUID FAST_DFU_SERVICE_UUID = UUID.fromString("A6ED0701-D344-460A-8075-B9E8EC90D71B");
    public final static UUID FAST_DFU_CMD_CHARACTERISTIC_UUID = UUID.fromString("A6ED0702-D344-460A-8075-B9E8EC90D71B");
    public final static UUID FAST_DFU_DAT_CHARACTERISTIC_UUID = UUID.fromString("A6ED0703-D344-460A-8075-B9E8EC90D71B");

    protected long defaultTimeout = 3000;
    protected BlockingBle ble = null;
    protected boolean isAppBootloaderSolution = false;
    protected int dfuProtocolVersion = 0;
    protected final HexSerializer rcvCmdBuf = new HexSerializer(0);

    BluetoothGattService fastDfuSvc;
    BluetoothGattCharacteristic cmdChr;
    BluetoothGattCharacteristic datChr;

    synchronized public void bindTo(BlockingBle ble) throws Throwable {
        if (ble == null) {
            throw new Error("bindTo(null)");
        }

        if (!ble.isConnected()) {
            throw new Error("The device is not connected. Please connect and try again.");
        }

        this.ble = ble;

        List<BluetoothGattService> list = ble.queryServices(FAST_DFU_SERVICE_UUID);
        if (list.isEmpty()) {
            throw new Error("Not found required service: " + FAST_DFU_SERVICE_UUID.toString());
        }

        fastDfuSvc = list.get(0);
        cmdChr = ble.queryCharacteristic(fastDfuSvc, FAST_DFU_CMD_CHARACTERISTIC_UUID);
        datChr = ble.queryCharacteristic(fastDfuSvc, FAST_DFU_DAT_CHARACTERISTIC_UUID);

        if (cmdChr == null || datChr == null) {
            final StringBuilder builder = new StringBuilder();
            builder.append("Not found required characteristic: ");
            if (cmdChr == null) {
                builder.append("CMD<").append(FAST_DFU_CMD_CHARACTERISTIC_UUID.toString()).append(">");
            }
            if (cmdChr == null && datChr == null) {
                builder.append(", ");
            }
            if (datChr == null) {
                builder.append("DAT<").append(FAST_DFU_DAT_CHARACTERISTIC_UUID.toString()).append(">");
            }
            throw new Error(builder.toString());
        }

        ble.enableNotification(cmdChr, true);
    }

    public void sendDat(byte[] dat, DataProgressListener listener) throws Throwable {
        if (dat == null) {
            return;
        }

        final BlockingBle ble = this.ble;
        if (ble == null) {
            throw new Error("sendDat(): please call bindTo() firstly.");
        }

        final int properties = datChr.getProperties();
        if (0 != (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) {
            ble.writeChrWithoutResponse(datChr, defaultTimeout, dat, 0, dat.length, listener);
        } else if (0 != (properties & BluetoothGattCharacteristic.PROPERTY_WRITE)) {
            ble.writeChrWithResponse(datChr, defaultTimeout, dat, 0, dat.length, listener);
        } else {
            throw new Error("sendDat(): DAT<" + datChr.getUuid().toString() + "> is not writable. Properties = " + properties);
        }
    }

    public void sendCmd(byte[] cmd) throws Throwable {
        if (cmd == null) {
            return;
        }

        final BlockingBle ble = this.ble;
        if (ble == null) {
            throw new Error("sendCmd(): please call bindTo() firstly.");
        }

        final int properties = cmdChr.getProperties();
        if (0 != (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) {
            ble.writeChrWithoutResponse(cmdChr, defaultTimeout, cmd, 0, cmd.length, null);
        } else if (0 != (properties & BluetoothGattCharacteristic.PROPERTY_WRITE)) {
            ble.writeChrWithResponse(cmdChr, defaultTimeout, cmd, 0, cmd.length, null);
        } else {
            throw new Error("sendCmd(): CMD<" + cmdChr.getUuid().toString() + "> is not writable. Properties = " + properties);
        }
    }

    public HexSerializer waitAck() throws Throwable {
        final BlockingBle ble = this.ble;
        if (ble == null) {
            throw new Error("waitAck(): please call bindTo() firstly.");
        }

        final byte[] chunk = ble.readNtf(this.cmdChr, defaultTimeout);

        if (chunk != null) {
            rcvCmdBuf.setBuffer(chunk);
            rcvCmdBuf.setPos(0);
        } else {
            rcvCmdBuf.setRange(0, 0);
            rcvCmdBuf.setPos(0);
            throw new TimeoutException("waitAck() timeout after " + defaultTimeout + "ms.");
        }

        return rcvCmdBuf;
    }
}


