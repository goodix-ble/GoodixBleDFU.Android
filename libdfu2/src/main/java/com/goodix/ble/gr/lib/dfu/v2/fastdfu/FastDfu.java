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

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.goodix.ble.gr.lib.com.ILogger;
import com.goodix.ble.gr.lib.com.ble.BlockingBle;
import com.goodix.ble.gr.lib.dfu.v2.DfuProgressListener;
import com.goodix.ble.gr.lib.dfu.v2.DfuProgressListenerWrapperForUi;
import com.goodix.ble.gr.lib.dfu.v2.pojo.DfuFile;

import java.io.InputStream;

public class FastDfu {
    private static final String TAG = "EasyDfu2";

    private ILogger logger = null;

    private Thread currentTask = null;

    private final DfuProgressListenerWrapperForUi listenerWrapper = new DfuProgressListenerWrapperForUi();

    public void setLogger(ILogger logger) {
        this.logger = logger;
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
        if (ctx == null || target == null || file == null) return false;

        BlockingBle.setup(ctx);

        this.listenerWrapper.onDfuStart();

        currentTask = new Thread(new Runnable() {
            @Override
            public void run() {
                final DfuProgressListener listener = FastDfu.this.listenerWrapper;
                BlockingBle ble = null;

                try {
                    final DfuFile dfuFile = new DfuFile();
                    if (!dfuFile.load(file, true)) {
                        listener.onDfuError(dfuFile.getLastError(), new Error(dfuFile.getLastError()));
                        return;
                    }

                    final GR5xxxFastDfu fast = new GR5xxxFastDfu();
                    fast.setLogger(logger);

                    ble = new BlockingBle(target);
                    ble.setLogger(FastDfu.this.logger);

                    ble.connect();
                    ble.discoverServices();
                    ble.setMtu(247);

                    fast.bindTo(ble);

                    fast.update(true, false, dfuFile, false, 0, listener);
                    Thread.sleep(200); /* waiting for the last cmd arrived */

                    listener.onDfuComplete();
                } catch (Throwable e) {
                    listener.onDfuError(e.getMessage(), new Error(e));
                    e.printStackTrace();
                } finally {
                    if (ble != null) {
                        try {
                            ble.disconnect();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                    FastDfu.this.currentTask = null;
                }
            }
        }, "startDfu");

        this.currentTask.start();

        return true;
    }

    public boolean startDfuInCopyMode(Context ctx, BluetoothDevice target, InputStream file, int copyAddr) {
        if (ctx == null || target == null || file == null) return false;

        BlockingBle.setup(ctx);

        this.listenerWrapper.onDfuStart();

        currentTask = new Thread(new Runnable() {
            @Override
            public void run() {
                final DfuProgressListener listener = FastDfu.this.listenerWrapper;
                BlockingBle ble = null;

                try {
                    final DfuFile dfuFile = new DfuFile();
                    if (!dfuFile.load(file, true)) {
                        listener.onDfuError(dfuFile.getLastError(), new Error(dfuFile.getLastError()));
                        return;
                    }

                    final GR5xxxFastDfu fast = new GR5xxxFastDfu();
                    fast.setLogger(logger);

                    ble = new BlockingBle(target);
                    ble.setLogger(FastDfu.this.logger);

                    ble.connect();
                    ble.discoverServices();
                    ble.setMtu(247);

                    fast.bindTo(ble);

                    fast.update(true, false, dfuFile, true, copyAddr, listener);
                    Thread.sleep(200); /* waiting for the last cmd arrived */

                    listener.onDfuComplete();
                } catch (Throwable e) {
                    listener.onDfuError(e.getMessage(), new Error(e));
                    e.printStackTrace();
                } finally {
                    if (ble != null) {
                        try {
                            ble.disconnect();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                    FastDfu.this.currentTask = null;
                }
            }
        }, "startDfuAsCopy");

        this.currentTask.start();

        return true;
    }

    public boolean startUpdateResource(Context ctx, BluetoothDevice target, InputStream file, boolean useExtFlash, int rscStartAddress) {
        if (ctx == null || target == null || file == null) return false;

        BlockingBle.setup(ctx);

        this.listenerWrapper.onDfuStart();

        currentTask = new Thread(new Runnable() {
            @Override
            public void run() {
                final DfuProgressListener listener = FastDfu.this.listenerWrapper;
                BlockingBle ble = null;

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

                    final GR5xxxFastDfu fast = new GR5xxxFastDfu();
                    fast.setLogger(logger);

                    ble = new BlockingBle(target);
                    ble.setLogger(FastDfu.this.logger);

                    ble.connect();
                    ble.discoverServices();
                    ble.setMtu(247);

                    fast.bindTo(ble);

                    fast.update(false, useExtFlash, dfuFile, false, rscStartAddress, listener);
                    Thread.sleep(200); /* waiting for the last cmd arrived */

                    listener.onDfuComplete();
                } catch (Throwable e) {
                    listener.onDfuError(e.getMessage(), new Error(e));
                    e.printStackTrace();
                } finally {
                    if (ble != null) {
                        try {
                            ble.disconnect();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                    FastDfu.this.currentTask = null;
                }
            }
        }, "startResourceUpdate");

        this.currentTask.start();

        return true;
    }

}
