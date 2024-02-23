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

package com.goodix.ble.gr.lib.com;

import android.util.Log;

public class LogcatLogger implements ILogger {
    public static final LogcatLogger INSTANCE = new LogcatLogger();

    private int levelFilter = 0;

    public LogcatLogger setLevelFilter(int levelFilter) {
        this.levelFilter = levelFilter;
        return this;
    }

    @Override
    public void v(String tag, String msg) {
        if (ILogger.VERBOSE < levelFilter) {
            return;
        }
        Log.v(tag, msg);
    }

    @Override
    public void d(String tag, String msg) {
        if (ILogger.DEBUG < levelFilter) {
            return;
        }
        Log.d(tag, msg);
    }

    @Override
    public void i(String tag, String msg) {
        if (ILogger.INFO < levelFilter) {
            return;
        }
        Log.i(tag, msg);
    }

    @Override
    public void w(String tag, String msg) {
        if (ILogger.WARNING < levelFilter) {
            return;
        }
        Log.w(tag, msg);
    }

    @Override
    public void e(String tag, String msg) {
        if (ILogger.ERROR < levelFilter) {
            return;
        }
        Log.e(tag, msg);
    }

    @Override
    public void logRaw(long timestamp, int level, String tag, String msg) {
        if (level < levelFilter) {
            return;
        }

        switch (level) {
            case ILogger.VERBOSE:
                Log.v(tag, msg);
                break;
            case ILogger.DEBUG:
                Log.d(tag, msg);
                break;
            case ILogger.INFO:
                Log.i(tag, msg);
                break;
            case ILogger.WARNING:
                Log.w(tag, msg);
                break;
            case ILogger.ERROR:
                Log.e(tag, msg);
                break;
        }
    }
}
