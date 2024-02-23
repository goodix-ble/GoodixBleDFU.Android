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

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.goodix.ble.gr.lib.com.ILogger;
import com.goodix.ble.gr.lib.com.ble.BlockingBle;
import com.goodix.ble.gr.lib.com.ble.BlockingLeScanner;
import com.goodix.ble.gr.lib.dfu.v2.pojo.DfuFile;

import java.io.InputStream;

public class EasyDfu2 {
    private static final String TAG = "EasyDfu2";

    private ILogger logger = null;


    private byte[] ctrlCmd = null;
    private boolean isFastMode = false;

    private Thread currentTask = null;

    private final DfuProgressListenerWrapperForUi listenerWrapper = new DfuProgressListenerWrapperForUi();

    public void setLogger(ILogger logger) {
        this.logger = logger;
    }

    public void setFastMode(boolean isFastMode) {
        this.isFastMode = isFastMode;
    }

    public void setCtrlCmd(byte[] ctrlCmd) {
        this.ctrlCmd = ctrlCmd;
    }

    public void setListener(DfuProgressListener listener) {
        this.listenerWrapper.listener = listener;
    }

    public boolean cancel() {
        final Thread thread = currentTask;
        if (thread != null) {
            thread.interrupt();
            return true;
        }
        return false;
    }

    //接口函数
    public boolean startDfu(Context ctx, BluetoothDevice target, InputStream file) {
        BlockingBle.setup(ctx);

        final DfuProgressListener listener = EasyDfu2.this.listenerWrapper;
        listener.onDfuStart();

        currentTask = new Thread(new Runnable() {
            @Override
            public void run() {
                final GR5xxxDfu2 dfu2 = new GR5xxxDfu2();
                try {
                    final DfuFile dfuFile = new DfuFile();
                    if (!dfuFile.load(file, true)) {
                        listener.onDfuError(dfuFile.getLastError(), new Error(dfuFile.getLastError()));
                        return;
                    }

                    dfu2.setLogger(logger);

                    BlockingBle ble = new BlockingBle(target);
                    ble.setLogger(EasyDfu2.this.logger);

                    ble.connect();
                    ble.discoverServices();
                    ble.setMtu(247);

                    dfu2.bindTo(ble);

                    dfu2.updateFirmware(isFastMode, dfuFile, dfuFile.getImgInfo().bootInfo.loadAddr, ctrlCmd, listener);
                    Thread.sleep(200); /* waiting for the last cmd arrived */

                    listener.onDfuComplete();
                } catch (Throwable e) {
                    listener.onDfuError(e.getMessage(), new Error(e));
                    e.printStackTrace();
                } finally {
                    final BlockingBle ble = dfu2.getBondBle();
                    if (ble != null) {
                        try {
                            ble.disconnect();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                    EasyDfu2.this.currentTask = null;
                }
            }
        }, "startDfu");

        this.currentTask.start();

        return true;
    }

    /**
     * For DFU V1, e.g. GR5515, start OTA with copy mode.
     * For DFU V2, e.g. GR5525, start OTA with dual bank. The chip can give a recommend address through dfu_port_init() in dfu_port.c.
     *
     * @param copyAddr For DFU V1, if null, abort progress. For DFU V2, if null, use address got from chip.
     */
    public boolean startDfuInCopyMode(Context ctx, BluetoothDevice target, InputStream file, Integer copyAddr) {
        BlockingBle.setup(ctx);
        int writeAddress;

        if (copyAddr == null) {
            writeAddress = -1;
        } else {
            writeAddress = copyAddr;
        }

        final DfuProgressListener listener = EasyDfu2.this.listenerWrapper;
        listener.onDfuStart();

        currentTask = new Thread(new Runnable() {
            @Override
            public void run() {
                final GR5xxxDfu2 dfu2 = new GR5xxxDfu2();
                dfu2.setLogger(logger);

                try {
                    final DfuFile dfuFile = new DfuFile();
                    if (!dfuFile.load(file, true)) {
                        listener.onDfuError(dfuFile.getLastError(), new Error(dfuFile.getLastError()));
                        return;
                    }

                    BlockingBle ble = new BlockingBle(target);
                    ble.setLogger(EasyDfu2.this.logger);

                    ble.connect();
                    ble.discoverServices();
                    ble.setMtu(247);

                    dfu2.bindTo(ble);

                    dfu2.updateFirmware(isFastMode, dfuFile, writeAddress, ctrlCmd, listener);
                    Thread.sleep(200); /* waiting for the last cmd arrived */

                    listener.onDfuComplete();
                } catch (Throwable e) {
                    listener.onDfuError(e.getMessage(), new Error(e));
                    e.printStackTrace();
                } finally {
                    final BlockingBle ble = dfu2.getBondBle();
                    if (ble != null) {
                        try {
                            ble.disconnect();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                    EasyDfu2.this.currentTask = null;
                }
            }
        }, "startDfuAsCopy");

        this.currentTask.start();

        return true;
    }

    public boolean startUpdateResource(Context ctx, BluetoothDevice target, InputStream file, boolean isExtFlash, int startAddress) {
        if (ctx == null || target == null || file == null) return false;

        BlockingBle.setup(ctx);

        final DfuProgressListener listener = EasyDfu2.this.listenerWrapper;
        listener.onDfuStart();

        currentTask = new Thread(new Runnable() {
            @Override
            public void run() {
                final GR5xxxDfu2 dfu2 = new GR5xxxDfu2();
                dfu2.setLogger(logger);

                try {
                    final DfuFile dfuFile = new DfuFile();
                    dfuFile.load(file, true);
                    String errMsg = null;
                    if (dfuFile.getData() == null) {
                        errMsg = "Can't load resource file.";
                    } else if (dfuFile.getData().length < 1) {
                        errMsg = "Empty resource file.";
                    }
                    if (errMsg != null) {
                        listener.onDfuError(errMsg, new Error(errMsg));
                        return;
                    }

                    BlockingBle ble = new BlockingBle(target);
                    ble.setLogger(EasyDfu2.this.logger);

                    ble.connect();
                    ble.discoverServices();
                    ble.setMtu(247);

                    dfu2.bindTo(ble);

                    dfu2.updateResource(isExtFlash, isFastMode, dfuFile, startAddress, ctrlCmd, listener);
                    Thread.sleep(200); /* waiting for the last cmd arrived */

                    listener.onDfuComplete();
                } catch (Throwable e) {
                    listener.onDfuError(e.getMessage(), new Error(e));
                    e.printStackTrace();
                } finally {
                    final BlockingBle ble = dfu2.getBondBle();
                    if (ble != null) {
                        try {
                            ble.disconnect();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                    EasyDfu2.this.currentTask = null;
                }
            }
        }, "startResourceUpdate");

        this.currentTask.start();

        return true;
    }

    /**
     * For GR5515. Jump to boot firmware and reconnect to boot firmware. Update APP firmware.
     */
    public boolean startDfuWithDfuBoot(Context ctx, BluetoothDevice target, InputStream file, String macOfBootFw) {
        BlockingBle.setup(ctx);

        final DfuProgressListener listener = EasyDfu2.this.listenerWrapper;
        listener.onDfuStart();

        currentTask = new Thread(new Runnable() {
            @Override
            public void run() {
                final GR5xxxDfu2 dfu2 = new GR5xxxDfu2();
                dfu2.setLogger(logger);
                try {
                    final DfuFile dfuFile = new DfuFile();
                    if (!dfuFile.load(file, true)) {
                        listener.onDfuError(dfuFile.getLastError(), new Error(dfuFile.getLastError()));
                        return;
                    }

                    BlockingBle ble = new BlockingBle(target);
                    ble.setLogger(EasyDfu2.this.logger);

                    listener.onDfuProgress(0, 0, "Connect to APP firmware.");
                    ble.connect();
                    ble.discoverServices();
                    ble.setMtu(247);

                    dfu2.bindTo(ble);

                    listener.onDfuProgress(0, 0, "Jump to boot firmware.");
                    // send custom cmd to jump to boot firmware
                    dfu2.writeCtrlPoint(new byte[]{0x44, 0x4F, 0x4F, 0x47});
                    // release connection
                    Thread.sleep(100);
                    ble.disconnect();

                    listener.onDfuProgress(0, 0, "Scan for boot firmware: " + macOfBootFw);
                    // scan to check existence
                    final BlockingLeScanner scanner = new BlockingLeScanner(ctx);
                    final BlockingLeScanner.Report report = scanner.scanForDevice(31_000, macOfBootFw);
                    if (report == null) {
                        throw new Error("Not found the advertisement of boot firmware: " + macOfBootFw);
                    }

                    listener.onDfuProgress(0, 0, "Connect boot firmware.");
                    // reconnect to boot firmware,
                    final BlockingBle bootBle = new BlockingBle(macOfBootFw);
                    bootBle.connect();
                    bootBle.discoverServices();
                    bootBle.setMtu(247);
                    dfu2.bindTo(bootBle);

                    // and start upgrading.
                    dfu2.updateFirmware(isFastMode, dfuFile, dfuFile.getImgInfo().bootInfo.loadAddr, ctrlCmd, listener);
                    Thread.sleep(200); /* waiting for the last cmd arrived */

                    listener.onDfuComplete();
                } catch (Throwable e) {
                    listener.onDfuError(e.getMessage(), new Error(e));
                    e.printStackTrace();
                } finally {
                    final BlockingBle ble = dfu2.getBondBle();
                    if (ble != null) {
                        try {
                            ble.disconnect();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                    EasyDfu2.this.currentTask = null;
                }
            }
        }, "DfuV1WithBoot");

        this.currentTask.start();

        return true;
    }


    /**
     * Example for custom progress
     */
//    public boolean startSmartExample(Context ctx, BluetoothDevice target, InputStream file) {
//        BlockingBle.setup(ctx);
//
//        final DfuProgressListener listener = EasyDfu2.this.listenerWrapper;
//        listener.onDfuStart();
//
//        currentTask = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                final GR5xxxDfu2 dfu2 = new GR5xxxDfu2();
//                dfu2.setLogger(logger);
//
//                try {
//                    final DfuFile dfuFile = new DfuFile();
//                    if (!dfuFile.load(file, true)) {
//                        listener.onDfuError(dfuFile.getLastError(), new Error(dfuFile.getLastError()));
//                        return;
//                    }
//
//                    BlockingBle ble = new BlockingBle(target);
//                    ble.setLogger(EasyDfu2.this.logger);
//
//                    listener.onDfuProgress(0, 0, "Connect to APP firmware.");
//                    ble.connect();
//                    ble.discoverServices();
//                    ble.setMtu(247);
//
//                    dfu2.bindTo(ble);
//
//                    listener.onDfuProgress(0, 0, "Jump to boot firmware.");
//                    // send custom cmd to jump to boot firmware
//                    dfu2.writeCtrlPoint(new byte[]{0x44, 0x4F, 0x4F, 0x47});
//                    // release connection
//                    Thread.sleep(100);
//                    ble.disconnect();
//
//                    listener.onDfuProgress(0, 0, "Scan for boot firmware: " + macOfBootFw);
//                    // scan to check existence
//                    final BlockingLeScanner scanner = new BlockingLeScanner(ctx);
//                    final BlockingLeScanner.Report report = scanner.scanForDevice(31_000, macOfBootFw);
//                    if (report == null) {
//                        throw new Error("Not found the advertisement of boot firmware: " + macOfBootFw);
//                    }
//
//                    listener.onDfuProgress(0, 0, "Connect boot firmware.");
//                    // reconnect to boot firmware,
//                    final BlockingBle bootBle = new BlockingBle(macOfBootFw);
//                    bootBle.connect();
//                    bootBle.discoverServices();
//                    bootBle.setMtu(247);
//                    dfu2.bindTo(bootBle);
//
//                    // and start upgrading.
//                    dfu2.updateFirmware(isFastMode, dfuFile, dfuFile.getImgInfo().bootInfo.loadAddr, ctrlCmd, listener);
//
//                    listener.onDfuComplete();
//                } catch (Throwable e) {
//                    listener.onDfuError(e.getMessage(), new Error(e));
//                    e.printStackTrace();
//                } finally {
//                    final BlockingBle ble = dfu2.getBondBle();
//                    if (ble != null) {
//                        try {
//                            ble.disconnect();
//                        } catch (Throwable e) {
//                            e.printStackTrace();
//                        }
//                    }
//                    EasyDfu2.this.currentTask = null;
//                }
//            }
//        }, "DfuV1WithBoot");
//
//        this.currentTask.start();
//
//        return true;
//    }

}
