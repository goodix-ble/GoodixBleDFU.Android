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
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;

import com.goodix.ble.gr.lib.com.LogcatLogger;
import com.goodix.ble.gr.lib.com.StringLogger;
import com.goodix.ble.gr.lib.com.ble.BlockingBle;
import com.goodix.ble.gr.lib.com.ble.BlockingBleUtil;
import com.goodix.ble.gr.lib.dfu.v2.DfuProgressListener;
import com.goodix.ble.gr.lib.dfu.v2.EasyDfu2;
import com.goodix.ble.gr.lib.dfu.v2.GR5xxxDfu2;
import com.goodix.ble.gr.lib.dfu.v2.fastdfu.FastDfu;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.UUID;

public class TestCase {
    public static final StringLogger ramLogger = new StringLogger(512 * 1024);

//    public static boolean fastMode = true;

    public static EasyDfu2 testDfu(Context ctx, BluetoothDevice device, InputStream file, DfuProgressListener listener, boolean fastMode) {
        if (null == device){
            return  null;
        }
        final EasyDfu2 dfu2 = new EasyDfu2();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        dfu2.setLogger(ramLogger);
        dfu2.setListener(listener);
        dfu2.setFastMode(fastMode);
        dfu2.startDfu(ctx, device, file); // use different mode

        return dfu2;
    }

    public static EasyDfu2 testDfuCopyMode(Context ctx, BluetoothDevice device, InputStream file, Integer copyAddress, DfuProgressListener listener, boolean fastMode) {
        if (null == device){
            return  null;
        }
        final EasyDfu2 dfu2 = new EasyDfu2();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        dfu2.setLogger(ramLogger);
        dfu2.setListener(listener);
        dfu2.setFastMode(fastMode);
        dfu2.startDfuInCopyMode(ctx, device, file, copyAddress); // use different mode

        return dfu2;
    }

    public static EasyDfu2 testDfuResource(Context ctx, BluetoothDevice device, InputStream file, boolean extFlash, int writeAddress, DfuProgressListener listener, boolean fastMode) {
        if (null == device){
            return  null;
        }
        final EasyDfu2 dfu2 = new EasyDfu2();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        dfu2.setLogger(ramLogger);
        dfu2.setListener(listener);
        dfu2.setFastMode(fastMode);
        dfu2.startUpdateResource(ctx, device, file, extFlash, writeAddress);

        return dfu2;
    }

    public static FastDfu testFastDfu(Context ctx, BluetoothDevice device, InputStream file, DfuProgressListener listener) {
        if (null == device){
            return  null;
        }
        final FastDfu fastDfu = new FastDfu();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        fastDfu.setLogger(ramLogger);
        fastDfu.setListener(listener);
        fastDfu.startDfu(ctx, device, file); // use different mode

        return fastDfu;
    }

    public static FastDfu testFastDfuCopyMode(Context ctx, BluetoothDevice device, InputStream file, int copyAddress, DfuProgressListener listener) {
        if (null == device){
            return  null;
        }
        final FastDfu fastDfu = new FastDfu();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        fastDfu.setLogger(ramLogger);
        fastDfu.setListener(listener);
        fastDfu.startDfuInCopyMode(ctx, device, file, copyAddress); // use different mode

        return fastDfu;
    }

    public static FastDfu testFastDfuResource(Context ctx, BluetoothDevice device, InputStream file, boolean extFlash, int writeAddress, DfuProgressListener listener) {
        if (null == device){
            return  null;
        }
        final FastDfu fastDfu = new FastDfu();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        fastDfu.setLogger(ramLogger);
        fastDfu.setListener(listener);
        fastDfu.startUpdateResource(ctx, device, file, extFlash, writeAddress); // use different mode

        return fastDfu;
    }

    /**
     * For the pair of ble_app_template_dfu and ble_dfu_boot.
     */
    public static EasyDfu2 testAppTemplateDfuAndDfuBoot(Context ctx, BluetoothDevice device, InputStream file, DfuProgressListener listener) {
        if (null == device){
            return  null;
        }
        // get the MAC of boot firmware
        String bootMac = GR5xxxDfu2.changeMacAddress(device.getAddress(), +1);

        final EasyDfu2 dfu2 = new EasyDfu2();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        dfu2.setLogger(ramLogger);
        dfu2.setListener(listener);
        dfu2.setFastMode(false);
        dfu2.startDfuWithDfuBoot(ctx, device, file, bootMac);

        return dfu2;
    }

    public static void testPermissionCheck(Context ctx, ActivityResultLauncher<String[]> permissionsLauncher) {
        if (!BlockingBleUtil.checkPermission(ctx, permissionsLauncher)) {
            Toast.makeText(ctx, "请授予定位权限", Toast.LENGTH_LONG).show();
        }
    }

    public static void testBlockingBle(Context ctx, BluetoothDevice device) {
        BlockingBle.setup(ctx);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final BlockingBle ble = new BlockingBle(device);
                    ble.setLogger(new LogcatLogger());
                    ble.connect();

                    Thread.sleep(3000);
                    ble.discoverServices();
                    ble.setMtu(247);

                    final BluetoothGattCharacteristic deviceNameChr = ble.acquireCharacteristic(UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb"));
                    final byte[] bytes = ble.readChr(deviceNameChr, 0);
                    Log.e(">>>>>", "name:" + new String(bytes, 0, bytes.length, Charset.defaultCharset()));

                    // prepare transmitting test.
                    Thread.sleep(3000);
                    final BluetoothGattCharacteristic rxChr = ble.acquireCharacteristic(UUID.fromString("a6ed0403-d344-460a-8075-b9e8ec90d71b"));
                    final byte[] dat = new byte[244 * 1024];
                    int pos = 0;
                    for (int i = 0; i < 1024; i++) {
                        Arrays.fill(dat, pos, pos + 244, (byte) i);
                        pos += 244;
                    }
                    // send data and calc data rate.
                    final long start = SystemClock.elapsedRealtimeNanos();
                    ble.writeChrWithoutResponse(rxChr, 3000, dat, 0, dat.length, null);
                    final long end = SystemClock.elapsedRealtimeNanos();
                    int speedKBps = (int) (dat.length * 1000000000L / 1024L / (end - start));
                    String speedStr = "Speed: " + speedKBps + "KBps, Size = " + dat.length + ", time = " + (end - start);
                    Log.e("$$$$$", speedStr);

                    Thread.sleep(3000);
                    ble.disconnect();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }
}
