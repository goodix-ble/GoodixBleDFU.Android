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

package com.goodix.ble.gr.lib.dfu.v2;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import com.goodix.ble.gr.lib.com.DataProgressListener;
import com.goodix.ble.gr.lib.com.HexSerializer;
import com.goodix.ble.gr.lib.com.HexString;
import com.goodix.ble.gr.lib.com.ble.BlockingBle;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class DfuProfile {
    public final static UUID DFU_SERVICE_UUID = UUID.fromString("a6ed0401-d344-460a-8075-b9e8ec90d71b");
    public final static UUID DFU_NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("a6ed0402-d344-460a-8075-b9e8ec90d71b");
    public final static UUID DFU_WRITE_CHARACTERISTIC_UUID = UUID.fromString("a6ed0403-d344-460a-8075-b9e8ec90d71b");
    public final static UUID DFU_CONTROL_CHARACTERISTIC_UUID = UUID.fromString("a6ed0404-d344-460a-8075-b9e8ec90d71b");

    protected BlockingBle ble = null;

    protected final HexSerializer rcvCmdBuf = new HexSerializer(2048);
    protected long defaultTimeout = 10_000;
    protected boolean isAppBootloaderSolution = false;
    protected int dfuProtocolVersion = 0;

    protected BluetoothGattService dfuSvc;
    protected BluetoothGattCharacteristic notifyChr;
    protected BluetoothGattCharacteristic writeChr;
    protected BluetoothGattCharacteristic ctrlChr;

    synchronized public void bindTo(BlockingBle ble) throws Throwable {
        if (ble == null) {
            throw new Error("bindTo(null)");
        }

        if (!ble.isConnected()) {
            throw new Error("The device is not connected. Please connect and try again.");
        }

        this.ble = ble;

        List<BluetoothGattService> list = ble.queryServices(DFU_SERVICE_UUID);
        if (list.isEmpty()) {
            throw new Error("Not found required service: " + DFU_SERVICE_UUID.toString());
        }

        dfuSvc = list.get(0);
        notifyChr = ble.queryCharacteristic(dfuSvc, DFU_NOTIFY_CHARACTERISTIC_UUID);
        writeChr = ble.queryCharacteristic(dfuSvc, DFU_WRITE_CHARACTERISTIC_UUID);
        ctrlChr = ble.queryCharacteristic(dfuSvc, DFU_CONTROL_CHARACTERISTIC_UUID);

        this.isAppBootloaderSolution = (ctrlChr.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
        if (this.isAppBootloaderSolution) {
            dfuProtocolVersion = 2; // at least
        } else {
            dfuProtocolVersion = 1; // must
        }

        if (notifyChr == null || writeChr == null || ctrlChr == null) {
            StringBuilder msg = new StringBuilder(512);
            msg.append("Not found required characteristic: ");
            if (notifyChr == null) {
                msg.append(" TX<").append(DFU_NOTIFY_CHARACTERISTIC_UUID).append(">");
            }
            if (writeChr == null) {
                msg.append(" RX<").append(DFU_WRITE_CHARACTERISTIC_UUID).append(">");
            }
            if (ctrlChr == null) {
                msg.append(" CTRL<").append(DFU_CONTROL_CHARACTERISTIC_UUID).append(">");
            }

            throw new Error(msg.toString());
        }

        ble.enableNotification(notifyChr, true);
    }

    public synchronized BlockingBle getBondBle() {
        return ble;
    }

    //命令收发接口
    public void writeCtrlPoint(byte[] data) throws Throwable {
        if (data == null) {
            return;
        }

        final BlockingBle ble = this.ble;
        if (ble == null) {
            throw new Error("writeCtrlPoint(): please call bindTo() firstly.");
        }

        final int properties = ctrlChr.getProperties();
        if (0 != (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) {
            ble.writeChrWithoutResponse(ctrlChr, defaultTimeout, data, 0, data.length, null);
        } else if (0 != (properties & BluetoothGattCharacteristic.PROPERTY_WRITE)) {
            ble.writeChrWithResponse(ctrlChr, defaultTimeout, data, 0, data.length, null);
        } else {
            throw new Error("writeCtrlPoint(): CTRL<" + ctrlChr.getUuid().toString() + "> is not writable.");
        }
    }

    public void sendCmdRaw(byte[] cmdFrame, DataProgressListener progressListener) throws Throwable {
        if (cmdFrame == null) {
            return;
        }

        final BlockingBle ble = this.ble;
        if (ble == null) {
            throw new Error("sendCmdRaw(): please call bindTo() firstly.");
        }

        final int properties = writeChr.getProperties();
        if (0 != (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) {
            ble.writeChrWithoutResponse(writeChr, defaultTimeout, cmdFrame, 0, cmdFrame.length, progressListener);
        } else if (0 != (properties & BluetoothGattCharacteristic.PROPERTY_WRITE)) {
            ble.writeChrWithResponse(writeChr, defaultTimeout, cmdFrame, 0, cmdFrame.length, progressListener);
        } else {
            throw new Error("sendCtrlCmd(): RX<" + writeChr.getUuid().toString() + "> is not writable.");
        }
    }

    public void sendCmd(int opcode, byte[] param) throws Throwable {
        int paramSize = 0;
        if (param != null && param.length > 0) {
            paramSize = param.length;
        }

        final HexSerializer frame = new HexSerializer(2 + 2 + 2 + paramSize + 2);
        frame.put(2, 0x4744);
        frame.put(2, opcode);
        frame.put(2, paramSize);

        if (paramSize > 0) {
            frame.put(param);
        }

        final int checksum = frame.getChecksum(2, 2 + 2 + paramSize);
        frame.put(2, checksum);

        sendCmdRaw(frame.getBuffer(), null);
    }

    public HexSerializer rcvCmd(int opcode) throws Throwable {
        final BlockingBle ble = this.ble;
        if (ble == null) {
            throw new Error("rcvCmd(): please call bindTo() firstly.");
        }
        int read = ble.readNtf(this.notifyChr, defaultTimeout, rcvCmdBuf.getBuffer(), 0, 6);
        if (read != 6) {
            if (read > 0) {
                final HexString msg = new HexString();
                msg.append("rcvCmd(): Failed to get header of cmd. Got bytes: ")
                        .appendHex(rcvCmdBuf.getBuffer(), 0, read);
                throw new Error(msg.toString());
            } else {
                throw new Error("rcvCmd(): Failed to get header of cmd.");
            }
        }

        rcvCmdBuf.setRangeAll();
        rcvCmdBuf.setPos(0);

        final int magicNum = rcvCmdBuf.get(2);
        final int rcvOpcode = rcvCmdBuf.get(2);
        final int paramLen = rcvCmdBuf.get(2);
        if (magicNum != 0x4744) {
            throw new Error("rcvCmd(): Error frame header: " + magicNum);
        }
        if (rcvOpcode != opcode) {
            throw new Error("rcvCmd(): Unexpected opcode: " + rcvOpcode);
        }
        if (paramLen + 8 > rcvCmdBuf.getBuffer().length) {
            throw new Error("rcvCmd(): Large length of param: " + paramLen);
        }

        read = ble.readNtf(notifyChr, defaultTimeout, rcvCmdBuf.getBuffer(), 6, paramLen + 2);

        rcvCmdBuf.setReadonly(true);
        rcvCmdBuf.setRange(6, paramLen);
        rcvCmdBuf.setPos(0);

        if (read != paramLen + 2) {
            throw new TimeoutException();
        }

        return rcvCmdBuf;
    }
}


