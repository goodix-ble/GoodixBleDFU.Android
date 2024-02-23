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

import com.goodix.ble.gr.lib.com.DataProgressListener;
import com.goodix.ble.gr.lib.com.HexSerializer;
import com.goodix.ble.gr.lib.com.HexString;
import com.goodix.ble.gr.lib.com.ILogger;
import com.goodix.ble.gr.lib.dfu.v2.DfuProgressListener;
import com.goodix.ble.gr.lib.dfu.v2.pojo.BootInfo;
import com.goodix.ble.gr.lib.dfu.v2.pojo.DfuFile;
import com.goodix.ble.gr.lib.dfu.v2.utils.MiscUtils;

public class GR5xxxFastDfu extends FastDfuProfile {
    private static final String TAG = "GR5xxxFastDfu";
    private ILogger logger = null;

    public void setLogger(ILogger logger) {
        this.logger = logger;
    }

    //DFU任务
    public int getFastDfuVersion() throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "getFastDfuVersion() called");
        }

        final HexSerializer ack = new Cmd(CmdOpcode.GET_VERSION)
                .sendCmd();

        final int version = ack.get(1);
        dfuProtocolVersion = version;
        return version;
    }

    public void setFlashType(boolean isExtFlash) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "selectFlashType() called with: isExtFlash = [" + isExtFlash + "]");
        }

        new Cmd(CmdOpcode.SELECT_FLASH_TYPE, 1)
                .putParam(1, isExtFlash ? 1 : 0)
                .sendCmd();
    }

    public void eraseFlash(int writeAddress, int writeSize, DfuProgressListener listener) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "eraseFlash() called with: writeAddress = [" + writeAddress + "], writeSize = [" + writeSize + "]");
        }

        final int sizeOfSector = 4 * 1024;
        int eraseSectorNum;

        eraseSectorNum = (writeSize + sizeOfSector - 1) / sizeOfSector;

        new Cmd(CmdOpcode.ERASE_FLASH, 8)
                .putParam(4, writeAddress)
                .putParam(4, writeSize)
                .postCmd();

        while (true) {
            // the size of parameter of ACK is variable. it will be 2 bytes or 4 bytes;
            final HexSerializer ack = waitAck();
            final int ackOpcode = ack.get(1);
            final int phase = ack.get(1);
            if (ackOpcode != CmdOpcode.ERASE_FLASH || ack.getRangeSize() < 2) {
                final HexString builder = new HexString(128);
                builder.append("eraseFlash(): Response = ");
                builder.appendHex(ack.copyRangeBuffer());
                throw new Error(builder.toString());
            }

            switch (phase) {
                case 0x00:
                    throw new Error("eraseFlash(): writeAddress is not 4K aligned.");
                case 0x01:
                    final int totalSector = ack.get(2);
                    if (logger != null) {
                        logger.d(TAG, "eraseFlash(): Start erasing " + totalSector + " sector(s).");
                    }
                    break;
                case 0x02:
                    final int erasedSectorCnt = ack.get(2);
                    if (logger != null)
                        logger.d(TAG, "eraseFlash(): " + erasedSectorCnt + "/" + eraseSectorNum + " sectors are erased.");
                    if (listener != null) {
                        listener.onDfuProgress(100 * erasedSectorCnt / eraseSectorNum, 0, "Erasing...");
                    }
                    break;
                case 0x03:
                    if (logger != null) logger.d(TAG, "eraseFlash(): Complete.");
                    return;
                case 0x04:
                    throw new Error("eraseFlash(): Overlap running firmware.");
                case 0x05:
                    throw new Error("eraseFlash(): Failed to erase.");
                case 0x06:
                    throw new Error("eraseFlash(): No EXT flash.");
                default:
                    throw new Error("eraseFlash(): Unknown code.");
            }
        }
    }

    public int getBufferSize(int fastDfuVersion) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "getBufferSize() called with: fastDfuVersion = [" + fastDfuVersion + "]");
        }

        if (fastDfuVersion < 3) {
            final HexSerializer ack = new Cmd(CmdOpcode.GET_BUFFER_SIZE)
                    .sendCmd();
            return ack.get(4);
        } else {
            return 4096;
        }
    }

    public void programFlash(int fastDfuVersion, DfuFile dfuFile, int bufferSize, DfuProgressListener listener) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "programFlash() called with: fastDfuVersion = [" + fastDfuVersion + "], dfuFile = [" + dfuFile + "], bufferSize = [" + bufferSize + "], listener = [" + listener + "]");
        }

        final byte[] fileData = dfuFile.getData();
        if (fastDfuVersion >= 3) {
            //发送全部数据
            if (listener != null) {
                sendDat(fileData, new DataProgressListener() {
                    @Override
                    public void onDataProcessed(Object data, int processedBytes, int totalBytes, long intervalTime, long totalTime) {
                        listener.onDfuProgress(100 * processedBytes / totalBytes, (int) (processedBytes * 1000L / totalTime), "Program flash...");
                    }
                });
            } else {
                sendDat(fileData, null);
            }

            //编码+发送
            new Cmd(CmdOpcode.FLUSH_FLASH).sendCmd();

        } else {
            final int totalSize = fileData.length;
            final long startTime = System.currentTimeMillis();
            int pos = 0;
            byte[] buf = new byte[bufferSize];
            while (pos < totalSize) {
                // process a block
                int blockSize = totalSize - pos;
                if (blockSize > bufferSize) {
                    blockSize = bufferSize;
                } else {
                    buf = new byte[blockSize];
                }

                // copy data
                System.arraycopy(fileData, pos, buf, 0, blockSize);
                sendDat(buf, null);

                pos += blockSize;

                if (listener != null) {
                    long now = System.currentTimeMillis();
                    listener.onDfuProgress(100 * pos / totalSize, (int) (pos * 1000L / (now - startTime)), "Program flash...");
                }

                // wait command
                final HexSerializer ack = waitAck();
                final int opcode = ack.get(1);
                switch (opcode) {
                    case CmdOpcode.FLOW_CTRL_PAUSE:
                        throw new Error("programFlash(): FlowCtrl = true, buffer overflowed.");
                    case CmdOpcode.FLOW_CTRL_RESUME:
                        throw new Error("programFlash(): FlowCtrl = false, not allowed.");
                    case CmdOpcode.NEXT_BUFFER:
                        // continue;
                        break;
                    default:
                        throw new Error("programFlash(): Unknown code: " + opcode);
                }
            }

            //编码+发送
            new Cmd(CmdOpcode.FLUSH_FLASH).sendCmd();
        }
    }


    public void verifyChecksum(DfuFile dfuFile) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "verifyChecksum() called");
        }

        final int fileChecksum = dfuFile.getFileChecksum();

        final HexSerializer ack = new Cmd(CmdOpcode.VERIFY_CHECKSUM, 4)
                .putParam(4, fileChecksum)
                .sendCmd();

        int rcvChecksum = ack.get(4);
        if (rcvChecksum != fileChecksum) {
            throw new Error("verifyChecksum(): Incorrect checksum. File = " + fileChecksum + ", Flash = " + rcvChecksum);
        }
    }

    public void reboot(DfuFile dfuFile, boolean copyMode, int copyAddress) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "reboot() called with: dfuFile = [" + dfuFile + "], copyMode = [" + copyMode + "], copyAddress = [" + copyAddress + "]");
        }

        final Cmd cmd;
        if (copyMode) {
            cmd = new Cmd(CmdOpcode.START_COPY, 40 + 4 + 4);
            dfuFile.getImgInfo().writeToData(cmd);
            cmd.putParam(4, copyAddress);
            cmd.putParam(4, dfuFile.getData().length);
        } else {
            cmd = new Cmd(CmdOpcode.WRITE_BOOT, 40);
            dfuFile.getImgInfo().writeToData(cmd);
        }
        cmd.postCmd();
    }

    // only DfuProgressListener.onDfuProgress() is used.
    public void update(boolean updateFw, boolean toExtFlash, DfuFile fwFile, boolean useCopyMode, int writeAddress, DfuProgressListener listener) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "update() called with: updateFw = [" + updateFw + "], toExtFlash = [" + toExtFlash + "], fwFile = [" + fwFile + "], useCopyMode = [" + useCopyMode + "], writeAddress = [" + writeAddress + "], listener = [" + listener + "]");
        }

        final int fileSize = fwFile.getData().length;

        if (updateFw) {
            toExtFlash = false; // ignored if update firmware.
            if (!useCopyMode) {
                writeAddress = fwFile.getImgInfo().bootInfo.loadAddr;
            }
        } else {
            useCopyMode = false; // ignored if update resource.
        }

        if (updateFw && useCopyMode) {
            final BootInfo fwBootInfo = fwFile.getImgInfo().bootInfo;
            final int copyAddress = writeAddress;
            if (fwBootInfo.hasOverlap(copyAddress, fileSize)) {
                final StringBuilder builder = new StringBuilder(128);
                builder.append("Copy Address has overlapped new firmware: ");
                MiscUtils.appendOverlapInfo(builder, copyAddress, fileSize, fwBootInfo.loadAddr, fileSize);
                throw new Error(builder.toString());
            }
        }

        if (listener != null) {
            listener.onDfuProgress(0, 0, "Get version");
        }
        final int fastDfuVersion = getFastDfuVersion();

        if (listener != null) {
            listener.onDfuProgress(0, 0, "Get size of buffer");
        }
        final int bufferSize = getBufferSize(fastDfuVersion);

        if (listener != null) {
            if (updateFw) {
                listener.onDfuProgress(0, 0, "Set flash type as inner-flash");
            } else {
                listener.onDfuProgress(0, 0, "Use Ext-flash: " + toExtFlash);
            }
        }
        setFlashType(toExtFlash);

        eraseFlash(writeAddress, fileSize, listener);

        programFlash(fastDfuVersion, fwFile, bufferSize, listener);

        // compat GR5515 SDK V1.6.12. JIRA: https://jira.goodix.com/browse/BALPRO-3092
        Thread.sleep(200);

        verifyChecksum(fwFile);

        if (updateFw) {
            reboot(fwFile, useCopyMode, writeAddress);
        }
    }

    static class CmdOpcode {
        public static final int CMD_HEADER = 0x474f4f44;

        public static final int ERASE_FLASH = 0X01;
        public static final int FLUSH_FLASH = 0X02;
        public static final int VERIFY_CHECKSUM = 0x03;
        public static final int WRITE_BOOT = 0X04;
        public static final int SELECT_FLASH_TYPE = 0X05;
        public static final int FLOW_CTRL_PAUSE = 0X06;
        public static final int FLOW_CTRL_RESUME = 0X07;
        public static final int START_COPY = 0X08;
        public static final int GET_BUFFER_SIZE = 0X09;
        public static final int NEXT_BUFFER = 0X0A;
        public static final int GET_VERSION = 0x0B;
    }

    class Cmd extends HexSerializer {
        final int opcode;

        public Cmd(int opcode) {
            this(opcode, 0);
        }

        public Cmd(int opcode, int size) {
            super(4 + 1 + size);
            this.opcode = opcode;

            super.put(4, CmdOpcode.CMD_HEADER);
            super.put(1, opcode);
        }

        public Cmd putParam(int size, int val) {
            super.put(size, val);
            return this;
        }

        void postCmd() throws Throwable {
            GR5xxxFastDfu.this.sendCmd(getBuffer());
        }

        HexSerializer sendCmd() throws Throwable {
            // send cmd
            this.postCmd();
            // and wait ack
            final HexSerializer ack = waitAck();
            final int ackOpcode = ack.get(1);
            if (ackOpcode != opcode) {
                throw new Error("Unexpected ACK opcode = " + ackOpcode);
            }
            return ack;
        }
    }
}


