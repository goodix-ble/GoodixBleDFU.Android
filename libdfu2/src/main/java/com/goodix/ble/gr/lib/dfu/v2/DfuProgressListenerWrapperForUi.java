package com.goodix.ble.gr.lib.dfu.v2;

import android.os.Handler;
import android.os.Looper;

public class DfuProgressListenerWrapperForUi implements DfuProgressListener {
    public DfuProgressListener listener;

    final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onDfuStart() {
        if (listener != null) {
            final Inner inner = new Inner(listener, Inner.CALL_ON_DFU_START);
            uiHandler.post(inner);
        }
    }

    @Override
    public void onDfuProgress(int percent, int speed, String message) {
        if (listener != null) {
            final Inner inner = new Inner(listener, Inner.CALL_ON_DFU_PROGRESS);
            inner.percent = percent;
            inner.speed = speed;
            inner.message = message;
            uiHandler.post(inner);
        }
    }

    @Override
    public void onDfuComplete() {
        if (listener != null) {
            final Inner inner = new Inner(listener, Inner.CALL_ON_DFU_COMPLETE);
            uiHandler.post(inner);
        }
    }

    @Override
    public void onDfuError(String message, Error error) {
        if (listener != null) {
            final Inner inner = new Inner(listener, Inner.CALL_ON_DFU_ERROR);
            inner.message = message;
            inner.error = error;
            uiHandler.post(inner);
        }
    }


    static class Inner implements Runnable {
        private static final int CALL_ON_DFU_START = 0;
        private static final int CALL_ON_DFU_PROGRESS = 1;
        private static final int CALL_ON_DFU_COMPLETE = 2;
        private static final int CALL_ON_DFU_ERROR = 3;

        final DfuProgressListener listener;
        final int callType;

        public Inner(DfuProgressListener listener, int callType) {
            this.listener = listener;
            this.callType = callType;
        }

        int percent;
        int speed;
        String message;
        Error error;

        @Override
        public void run() {
            switch (callType) {
                case CALL_ON_DFU_START:
                    listener.onDfuStart();
                    break;
                case CALL_ON_DFU_PROGRESS:
                    listener.onDfuProgress(percent, speed, message);
                    break;
                case CALL_ON_DFU_COMPLETE:
                    listener.onDfuComplete();
                    break;
                case CALL_ON_DFU_ERROR:
                    listener.onDfuError(message, error);
                    break;
            }
        }
    }
}
