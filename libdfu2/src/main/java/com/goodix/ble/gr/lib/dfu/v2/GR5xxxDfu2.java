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

import android.util.Log;

import com.goodix.ble.gr.lib.com.DataProgressListener;
import com.goodix.ble.gr.lib.com.HexSerializer;
import com.goodix.ble.gr.lib.com.HexString;
import com.goodix.ble.gr.lib.com.ILogger;
import com.goodix.ble.gr.lib.com.ble.BlockingBle;
import com.goodix.ble.gr.lib.com.ble.BlockingBleUtil;
import com.goodix.ble.gr.lib.com.ble.BlockingLeScanner;
import com.goodix.ble.gr.lib.dfu.v2.pojo.BootInfo;
import com.goodix.ble.gr.lib.dfu.v2.pojo.DfuChipInfo;
import com.goodix.ble.gr.lib.dfu.v2.pojo.DfuFile;
import com.goodix.ble.gr.lib.dfu.v2.pojo.ImgInfo;
import com.goodix.ble.gr.lib.dfu.v2.utils.MiscUtils;

import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

public class GR5xxxDfu2 extends DfuProfile {
    private static final String TAG = "GR5xxxDfu2";
    private ILogger logger = null;

    private static final byte[] CTRL_POINT_PATTERN = new byte[]{0x44, 0x4F, 0x4F, 0x47};

    public void setLogger(ILogger logger) {
        this.logger = logger;
    }

    //任务
    //通用任务
    public DfuChipInfo getChipInfo() throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.v(TAG, "getChipInfo() called.");
        }

        this.sendCmd(CmdOpcode.GET_INFO, null);
        final HexSerializer rcvParam = this.rcvCmd(CmdOpcode.GET_INFO);

        int resp = rcvParam.get(1);
        if (resp != 1) throw new Error("getChipInfo(): response = " + resp);

        final DfuChipInfo chipInfo = new DfuChipInfo();
        chipInfo.readFromData(rcvParam);

        return chipInfo;
    }

    public int getAddressOfSCA(DfuChipInfo chipInfo) {
        if (chipInfo == null) {
            return 0x01000000;
        }
        switch (chipInfo.stackSVN) {
            case 0x00001EA8://GR5515_C1
            case 0x00000B88://GR5515_C4
                return 0x01000000;
            case 0XCA0F33C7://GR5525
            case 0xF83A64D9://GR5526
            case 0x00354083://GR5332_B0
            default:
                return 0x0020_0000;
        }
    }

    public StartupBootInfo getStartupBootInfo(int addressOfSCA) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "getStartupBootInfo() called with: SCA = [" + addressOfSCA + "]");
        }
        final int cmdOpcode = CmdOpcode.SYSTEM_CONFIG;
        //编码+发送
        HexSerializer cmdParam = new HexSerializer(7);
        cmdParam.put(1, 0);
        cmdParam.put(4, addressOfSCA);
        cmdParam.put(2, 24);
        super.sendCmd(cmdOpcode, cmdParam.getBuffer());
        //接收+解码
        HexSerializer rcvParam = super.rcvCmd(cmdOpcode);
        int resp = rcvParam.get(1);
        if (resp != 1) {
            throw new Error("getStartupBootInfo(): response = " + resp);
        }

        int op = rcvParam.get(1);
        int addr = rcvParam.get(4);
        int len = rcvParam.get(2);
        if (addr != addressOfSCA) {
            final HexString builder = new HexString(128);
            builder.append("getStartupBootInfo(): unexpected data from address: 0x");
            builder.appendHex(addr);
            throw new Error(builder.toString());
        }

        if (len != 24) {
            throw new Error("getStartupBootInfo(): unexpected data size: " + len);
        }

        final StartupBootInfo ret = new StartupBootInfo();
        ret.isEncrypted = (op & 0xf0) != 0x00;
        ret.bootInfo.readFromData(rcvParam);

        return ret;
    }

    public ImgInfoList getImgList(int addressOfSCA) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "getImgList() called with: SCA = [" + addressOfSCA + "]");
        }

        final int cmdOpcode = CmdOpcode.SYSTEM_CONFIG;
        final int IMG_INFO_COUNT = 10;
        //编码+发送
        HexSerializer cmdParam = new HexSerializer(7);
        cmdParam.put(1, 0);
        cmdParam.put(4, addressOfSCA + 0x40);
        cmdParam.put(2, ImgInfo.IMG_INFO_SIZE * IMG_INFO_COUNT);
        this.sendCmd(cmdOpcode, cmdParam.getBuffer());
        //接收+解码
        final HexSerializer rcvParam = this.rcvCmd(cmdOpcode);
        int resp = rcvParam.get(1);
        if (resp != 1) {
            throw new Error("getImgList(): response = " + resp);
        }

        int op = rcvParam.get(1);
        int addr = rcvParam.get(4);
        int len = rcvParam.get(2);
        if (addr != (addressOfSCA + 0x40)) {
            final byte[] dat = rcvParam.copyRangeBuffer();
            final HexString builder = new HexString(128);
            builder.append("getImgList(): unexpected data from address: 0x");
            builder.appendHex(addr);
            builder.append(", data[").append(dat.length).append("] ");
            builder.appendHex(dat);
            throw new Error(builder.toString());
        }

        if (len != ImgInfo.IMG_INFO_SIZE * IMG_INFO_COUNT) {
            throw new Error("getImgList(): unexpected data size: " + len);
        }

        final ImgInfoList imgInfoList = new ImgInfoList();
        imgInfoList.imgList = new ArrayList<>(IMG_INFO_COUNT);
        imgInfoList.isEncrypted = (op & 0xf0) != 0x00;

        ImgInfo imgInfo = new ImgInfo();

        for (int i = 0; i < IMG_INFO_COUNT; i++) {
            imgInfo.readFromData(rcvParam);
            if (imgInfo.pattern == ImgInfo.VALID_PATTERN) {
                imgInfoList.imgList.add(imgInfo);
                imgInfo = new ImgInfo();
            }
        }

        return imgInfoList;
    }

    public AppBootloaderExtraInfo getAppBootloaderExtraInfo() throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "getAppBootloaderExtraInfo() called.");
        }

        if (!isAppBootloaderSolution || dfuProtocolVersion < 2) {
            throw new Error("getAppBootloaderExtraInfo(): isAppBootloaderSolution = " + isAppBootloaderSolution + ", dfuProtocolVersion = " + dfuProtocolVersion);
        }

        final int cmdCode = CmdOpcode.GET_FW_INFO;
        this.sendCmd(cmdCode, null);
        final HexSerializer ackParam = this.rcvCmd(cmdCode);

        int resp = ackParam.get(1);
        if (resp != 1) throw new Error("getAppBootloaderExtraInfo(): response = " + resp);

        final AppBootloaderExtraInfo ret = new AppBootloaderExtraInfo();
        ret.recommendSaveAddress = ackParam.get(4);
        ret.position = ackParam.get(1);
        ret.appFwImgInfo = new ImgInfo().readFromData(ackParam);

        return ret;
    }

    public void tidyImgList(int targetAddress, int targetSize, BootInfo startupBootInfo, ArrayList<ImgInfo> imgList, int addressOfSCA) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "tidyImgList() called with: targetAddress = [" + targetAddress + "], targetSize = [" + targetSize + "], startupBootInfo = [" + startupBootInfo + "], imgList = [" + imgList + "], addressOfSCA = [" + addressOfSCA + "]");
        }

        if (startupBootInfo == null) return;
        if (imgList == null) return;

        ArrayList<ImgInfo> cleanImgList = new ArrayList<>();

        boolean isStartupBootInfoInImgList = false;
        boolean isChanged = false;
        for (ImgInfo imgInfo : imgList) {
            if (imgInfo.bootInfo.checksum == startupBootInfo.checksum && imgInfo.bootInfo.loadAddr == startupBootInfo.loadAddr) {
                isStartupBootInfoInImgList = true;
                break;
            }
        }
        // 将当前运行的固件的信息填补上
        if (!isStartupBootInfoInImgList) {
            final ImgInfo info = new ImgInfo();
            info.pattern = ImgInfo.VALID_PATTERN;
            info.version = 1;
            info.comments = "fromBoot";
            info.bootInfo.copy(startupBootInfo);
            cleanImgList.add(info);
            isChanged = true;
        }

        for (ImgInfo imgInfo : imgList) {
            if (imgInfo.pattern != ImgInfo.VALID_PATTERN) {
                continue;
            }
            if (imgInfo.bootInfo.hasOverlap(targetAddress, targetSize)) {
                isChanged = true;
                continue;
            }
            cleanImgList.add(imgInfo);
        }

        if (isChanged) {
            //将newImgList编码传输
            HexSerializer cmd = new HexSerializer(1 + 2 + 4 + 400);
            cmd.put(1, 0x01); // OP=0x01, write System Configuration to chip.
            cmd.put(4, addressOfSCA + 0x40); // ImgInfoList is stored at 0x40.
            cmd.put(2, 400);

            for (ImgInfo imgInfo : cleanImgList) {
                imgInfo.writeToData(cmd);
            }

            // fill remain bytes
            cmd.fill(400, 0xFF);

            sendCmd(CmdOpcode.SYSTEM_CONFIG, cmd.getBuffer());
            //接收+解码
            final HexSerializer rcvParam = rcvCmd(CmdOpcode.SYSTEM_CONFIG);
            int resp = rcvParam.get(1);
            if (resp != 1) throw new Error("tidyImgList(): Response = " + resp);
        }
    }

    //dfu相关任务
    public void enableDfuSchedule() throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "setDfuEnter() called");
        }

        writeCtrlPoint(CTRL_POINT_PATTERN);
    }

    public void setDfuModeOfChip(boolean doubleBank) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "setDfuModeOfChip() called with: doubleBank = [" + doubleBank + "]");
        }

        if (dfuProtocolVersion < 2) {
            throw new Error("setDfuModeOfChip(): dfuProtocolVersion = " + dfuProtocolVersion);
        }

        final byte[] param = new byte[1];
        if (doubleBank) {
            param[0] = 0x01; // background DFU with two banks.
        } else {
            param[0] = 0x02;
        }

        sendCmd(CmdOpcode.SET_DFU_MODE, param);
    }

    public void programStart(boolean updateFw, boolean toExtFlash, boolean withFastMode, DfuFile dfuFw, int writeAddress, EraseFlashProgressListener eraseListener) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "programStart() called with: updateFw = [" + updateFw + "], toExtFlash = [" + toExtFlash + "], withFastMode = [" + withFastMode + "], dfuFw = [x], writeAddress = [" + writeAddress + "], eraseListener = [x]");
        }

        if (dfuProtocolVersion < 2) {
            withFastMode = false;
            eraseListener = null;
            if (logger != null) {
                logger.w(TAG, "programStart(): FastMode is not supported.");
            }
        }

        int paramLen = 9;
        if (updateFw) {
            if (dfuFw.isValidDfuFile()) {
                if (toExtFlash) {
                    toExtFlash = false;
                    if (logger != null) {
                        logger.w(TAG, "programStart(): toExtFlash is ignored.");
                    }
                }
                paramLen = 41;
            } else {
                throw new Error("programStart(): Invalid firmware file.");
            }
        }

        final HexSerializer param = new HexSerializer(paramLen);

        if (toExtFlash) {
            param.put(1, withFastMode ? 0x03 : 0x01);
        } else {
            int type = withFastMode ? 0x02 : 0x00;
            if (dfuProtocolVersion >= 2){
                if (dfuFw.isSigned()) {
                    if (dfuFw.isEncrypted()) {
                        type |= 0x20; // signed=true, encrypt=true;
                    } else {
                        type |= 0x10; // singed=true, encrypt=false;
                    }
                }
            }
            // else {type |= 0x00;}  singed=false, encrypt=false;
            param.put(1, type);
        }

        if (updateFw) {
            ImgInfo imgInfo = dfuFw.getImgInfo();
            if (writeAddress != dfuFw.getImgInfo().bootInfo.loadAddr) {
                ImgInfo tmpImgInfo = new ImgInfo();
                tmpImgInfo.copy(imgInfo);
                tmpImgInfo.bootInfo.loadAddr = writeAddress;
                imgInfo = tmpImgInfo;
            }
            imgInfo.writeToData(param);
        } else {
            param.put(4, writeAddress);
            param.put(4, dfuFw.getData().length);
        }

        sendCmd(CmdOpcode.PROGRAM_START, param.getBuffer());
        HexSerializer rcvCmdParam = rcvCmd(CmdOpcode.PROGRAM_START);
        int resp = rcvCmdParam.get(1);

        if (resp != 1) {
            throw new Error("programStart(): Response = " + resp);
        }

        if (withFastMode) {
            // FastMode: the phase of erasing flash.
            int eraseState = rcvCmdParam.get(1);
            int totalSector = rcvCmdParam.get(2);

            if (eraseState != 0x01) {
                if (logger != null) {
                    logger.w(TAG, "programStart(): expected state is Erasing. But state = " + eraseState);
                }
            } else {
                if (logger != null) {
                    logger.d(TAG, "programStart(): Erasing...");
                }
            }

            while (totalSector > 0) {
                // wait erasing...
                rcvCmdParam = rcvCmd(CmdOpcode.PROGRAM_START);
                resp = rcvCmdParam.get(1);
                eraseState = rcvCmdParam.get(1);
                int erasedCnt = rcvCmdParam.get(2);

//                if (resp != 1 && (eraseState == 0x02 || eraseState == 0x03)) {
//                    throw new Error("programStart(): Response = " + resp);
//                }

                switch (eraseState) {
                    case 0x00:
                        throw new Error("programStart(): The write address is not 4K aligned.");
//                    case 0x01:
//                        if (logger != null) logger.d(TAG, "programStartFast: Start Erasing.");
//                        break;
                    case 0x02:
                        if (logger != null) {
                            logger.d(TAG, "programStart(): Erasing:" + erasedCnt);
                        }
                        if (eraseListener != null) {
                            eraseListener.onSectorErased(erasedCnt, totalSector);
                        }
                        break;
                    case 0x03:
                        if (logger != null) logger.d(TAG, "programStartFast: Complete Erase.");
                        totalSector = 0; // exit while().
                        break;
                    case 0x04:
                        throw new Error("programStart(): The current running firmware area is overlapped.");
                    case 0x05:
                        throw new Error("programStart(): Failed to erase.");
                    case 0x06:
                        throw new Error("programStart(): The area to be erased does not exist.");
                    default:
                        throw new Error("programStart(): Error state：" + eraseState);
                }
            }
        }
    }

    public void programFlash(boolean updateFw, boolean toExtFlash, boolean withFastMode, DfuFile dfuFw, int writeAddress, DataProgressListener progressListener) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            Log.d(TAG, "programFlash() called with: updateFw = [" + updateFw + "], toExtFlash = [" + toExtFlash + "], withFastMode = [" + withFastMode + "], dfuFw = [x], writeAddress = [" + writeAddress + "], progressListener = [x]");
        }

        if (dfuProtocolVersion < 2) {
            withFastMode = false;
            if (logger != null) {
                logger.w(TAG, "programFlash(): FastMode is not supported.");
            }
        }

        int totalBytes = dfuFw.getData().length;
        long startTime = System.currentTimeMillis();
        long reportTime = startTime;

        if (withFastMode) {
            if (progressListener != null) {
                sendCmdRaw(dfuFw.getData(), new DataProgressListener() {
                    @Override
                    public void onDataProcessed(Object data, int processedBytes, int totalBytes, long intervalTime, long totalTime) {
                        progressListener.onDataProcessed(dfuFw, processedBytes, totalBytes, intervalTime, totalTime);
                    }
                });
            } else {
                sendCmdRaw(dfuFw.getData(), null);
            }

            final HexSerializer rcvCmdParam = rcvCmd(CmdOpcode.PROGRAM_FLASH_FAST);
            final int resp = rcvCmdParam.get(1);
            if (resp != 1) throw new Error("programFlash(): Response = " + resp);
        } else {
            int pos = 0;
            final int MAX_SEGMENT_SIZE = 1024;
            HexSerializer cmdParam = new HexSerializer(1 + 4 + 2 + MAX_SEGMENT_SIZE);

            while (pos < totalBytes) {
                int segmentSize = MAX_SEGMENT_SIZE;
                if (pos + MAX_SEGMENT_SIZE > totalBytes) {
                    segmentSize = totalBytes - pos;
                    cmdParam = new HexSerializer(1 + 4 + 2 + segmentSize);
                }
                cmdParam.reset();

                cmdParam.put(1, toExtFlash ? 0x11 : 0x01); //
                cmdParam.put(4, writeAddress + pos);
                cmdParam.put(2, segmentSize);
                cmdParam.put(segmentSize, dfuFw.getData(), pos);

                sendCmd(CmdOpcode.PROGRAM_FLASH, cmdParam.getBuffer());
                final HexSerializer rcvCmdParam = rcvCmd(CmdOpcode.PROGRAM_FLASH);

                final int resp = rcvCmdParam.get(1);
                if (resp != 1) throw new Error("programFlash(): Response = " + resp);

                pos += segmentSize;
                if (progressListener != null) {
                    long now = System.currentTimeMillis();

                    progressListener.onDataProcessed(dfuFw, pos, totalBytes, now - reportTime, now - startTime);
                    reportTime = now;
                }
            }
        }
    }

    public void programEnd(boolean updateFw, boolean toExtFlash, boolean withFastMode, DfuFile dfuFw, boolean runImmediately) throws Throwable {
        final ILogger logger = this.logger;
        if (logger != null) {
            logger.d(TAG, "programEnd() called with: updateFw = [" + updateFw + "], toExtFlash = [" + toExtFlash + "], withFastMode = [" + withFastMode + "], dfuFw = [" + dfuFw + "]");
        }

        if (dfuProtocolVersion < 2) {
            withFastMode = false;
            if (logger != null) {
                logger.w(TAG, "programEnd(): FastMode is not supported.");
            }
        }

        int resetType;
        if (updateFw) {
            resetType = runImmediately ? 0x01 : 0x00; // reset and set as booting FW.
        } else {
            resetType = toExtFlash ? 0x12 : 0x02;
        }

        final HexSerializer cmdParam = new HexSerializer(1 + 4);
        cmdParam.put(1, resetType);
        cmdParam.put(4, dfuFw.getFileChecksum());

        sendCmd(CmdOpcode.PROGRAM_END, cmdParam.getBuffer());

        HexSerializer rcvCmdParam = null;

        try {
            // Timeout is allowed.
            rcvCmdParam = rcvCmd(CmdOpcode.PROGRAM_END);
        } catch (TimeoutException ignored) {
        }catch (Error err){
            if ((0x01 != resetType) || !err.getMessage().startsWith("Connection is lost")){
                //continue to throw error out except ConnectionError
                throw err;
            }
        }

        if (rcvCmdParam != null) {
            final int resp = rcvCmdParam.get(1);
            if (resp != 1) throw new Error("programEnd(): Response = " + resp);

            if (withFastMode) {
                final int checksum = rcvCmdParam.get(4);
                if (checksum != dfuFw.getFileChecksum()) {
                    throw new Error("programEnd(): Unexpected checksum = " + checksum);
                }
            }
        }
    }

    public void updateFirmware(boolean withFastMode, DfuFile dfuFw, int writeAddress, byte[] ctrlCmd, DfuProgressListener progressCallback) throws Throwable {
        // only DfuProgressListener.onDfuProgress() is used.

        boolean isDoubleBank = dfuFw.getImgInfo().bootInfo.loadAddr != writeAddress;
        // boolean isCopyMode = isDoubleBank;

        if (isAppBootloaderSolution) {
            enableDfuSchedule();

            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Load chip info...");
            }

            DfuChipInfo chipInfo = getChipInfo();
            final int addressOfSCA = getAddressOfSCA(chipInfo);

            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Load boot info...");
            }

            StartupBootInfo bootloader = getStartupBootInfo(addressOfSCA);

            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Load extra info...");
            }

            AppBootloaderExtraInfo extraInfo = getAppBootloaderExtraInfo();
            if (isDoubleBank && writeAddress == -1) {
                writeAddress = extraInfo.recommendSaveAddress;
                final HexString builder = new HexString(128);
                builder.append("Use recommended address from chip: 0x");
                builder.appendHex(writeAddress);
                final String tip = builder.toString();
                if (logger != null) {
                    logger.i(TAG, tip);
                }
                if (progressCallback != null) {
                    progressCallback.onDfuProgress(0, 0, tip);
                }
            }

            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Check overlap...");
            }

            // check encryption
            if (dfuFw.isEncrypted() != bootloader.isEncrypted) {
                throw new Error("updateFirmware(): Encryption is mismatch. FW = " + dfuFw.isEncrypted() + ", CHIP = " + bootloader.isEncrypted);
            }

            checkOverlapV2(true, false, dfuFw, writeAddress, addressOfSCA, bootloader.bootInfo, extraInfo.appFwImgInfo.bootInfo);

            if (isDoubleBank || extraInfo.position == AppBootloaderExtraInfo.CURRENT_FW_IS_APP) {
                setDfuModeOfChip(isDoubleBank);
                /* setDfuModeOfChip has no response, so delay sometime */
                Thread.sleep(500);
            }

            // 如果使用单区模式，且单区处于AppFW，那么会出现一次重启。
            if (!isDoubleBank && extraInfo.position == AppBootloaderExtraInfo.CURRENT_FW_IS_APP) {
                if (progressCallback != null) {
                    progressCallback.onDfuProgress(0, 0, "Jump to AppBootloader...");
                }

                String newDeviceMac = changeMacAddress(this.ble.targetDevice.getAddress(), +1);

                Thread.sleep(100);
                this.ble.disconnect();
                Thread.sleep(200);

                final BlockingLeScanner scanner = new BlockingLeScanner(BlockingBle.appCtx);
                final BlockingLeScanner.Report report = scanner.scanForDevice(31_000, newDeviceMac);
                if (report != null) {
                    final BlockingBle newBle = new BlockingBle(report.device);
                    newBle.connect();
                    newBle.discoverServices();
                    newBle.setMtu(247);
                    this.bindTo(newBle);
                    if (progressCallback != null) {
                        progressCallback.onDfuProgress(0, 0, "Time for bootloader to take a deep breath...");
                    }
                    Thread.sleep(2_000);
                } else {
                    // not found
                    throw new Error("updateFirmware(): Not found the advertisement of AppBootloader:" + newDeviceMac);
                }
            }

        } else {
            withFastMode = false; // not support

            if (ctrlCmd != null) {
                writeCtrlPoint(ctrlCmd);
            }

            DfuChipInfo chipInfo = getChipInfo();
            final int addressOfSCA = getAddressOfSCA(chipInfo);

            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Load boot info...");
            }

            StartupBootInfo runningFw = getStartupBootInfo(addressOfSCA);

            // check encryption
            if (dfuFw.isEncrypted() != runningFw.isEncrypted) {
                throw new Error("updateFirmware(): Encryption is mismatch. FW = " + dfuFw.isEncrypted() + ", CHIP = " + runningFw.isEncrypted);
            }

            checkOverlapV1(true, false, dfuFw, writeAddress, addressOfSCA, runningFw.bootInfo);

            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Load ImgInfo list...");
            }

            final ImgInfoList imgInfoList = getImgList(addressOfSCA);

            tidyImgList(dfuFw.getImgInfo().bootInfo.loadAddr, dfuFw.getData().length, runningFw.bootInfo, imgInfoList.imgList, addressOfSCA);
        }

        //下载数据
        if (progressCallback != null) {
            progressCallback.onDfuProgress(0, 0, "Downloading...");
        }

        programStart(true, false, withFastMode, dfuFw, writeAddress, new EraseFlashProgressListener() {
            @Override
            public void onSectorErased(int erased, int total) {
                if (progressCallback != null) {
                    int percent = 50 * erased / total;
                    progressCallback.onDfuProgress(percent, 0, "Erasing...");
                }
            }
        });

        final int usedProgressPercent;
        if (withFastMode) {
            usedProgressPercent = 50;
        } else {
            usedProgressPercent = 0;
        }

        programFlash(true, false, withFastMode, dfuFw, writeAddress, new DataProgressListener() {
            int lastReportPercent = -1;

            @Override
            public void onDataProcessed(Object data, int processedBytes, int totalBytes, long intervalTime, long totalTime) {
                if (progressCallback != null) {
                    int percent = usedProgressPercent + (100 - usedProgressPercent) * processedBytes / totalBytes;
                    if (lastReportPercent != percent) {
                        lastReportPercent = percent;
                        progressCallback.onDfuProgress(percent, (int) (processedBytes * 1000 / totalTime), "Programming...");
                    }
                }
            }
        });

        programEnd(true, false, withFastMode, dfuFw, true);
    }


    public void updateResource(boolean toExtFlash, boolean withFastMode, DfuFile dataFile, int writeAddress, byte[] ctrlCmd, DfuProgressListener progressCallback) throws Throwable {
        if (isAppBootloaderSolution) {
            enableDfuSchedule();

            // CHIP INFO
            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Load chip info...");
            }

            DfuChipInfo chipInfo = getChipInfo();
            final int addressOfSCA = getAddressOfSCA(chipInfo);

            // BOOT INFO
            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Load boot info...");
            }

            StartupBootInfo bootloader = getStartupBootInfo(addressOfSCA);

            // EXTRA INFO
            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Load extra info...");
            }

            AppBootloaderExtraInfo extraInfo = getAppBootloaderExtraInfo();

            // CHECK OVERLAP
            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Check overlap...");
            }

            checkOverlapV2(false, toExtFlash, dataFile, writeAddress, addressOfSCA, bootloader.bootInfo, extraInfo.appFwImgInfo.bootInfo);

            // setDfuModeOfChip(isDoubleBank); not required by updating resource.
        } else {
            withFastMode = false; // not support

            if (ctrlCmd != null) {
                writeCtrlPoint(ctrlCmd);
            }

            // CHIP INFO
            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Load chip info...");
            }

            DfuChipInfo chipInfo = getChipInfo();
            final int addressOfSCA = getAddressOfSCA(chipInfo);

            // BOOT INFO
            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Load boot info...");
            }

            StartupBootInfo runningFw = getStartupBootInfo(addressOfSCA);

            // CHECK OVERLAP
            checkOverlapV1(false, toExtFlash, dataFile, writeAddress, addressOfSCA, runningFw.bootInfo);

            // TIDY IMG INFO LIST
            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Load ImgInfo list...");
            }

            final ImgInfoList imgInfoList = getImgList(addressOfSCA);

            tidyImgList(writeAddress, dataFile.getData().length, runningFw.bootInfo, imgInfoList.imgList, addressOfSCA);
        }

        //下载数据
        if (progressCallback != null) {
            progressCallback.onDfuProgress(0, 0, "Downloading...");
        }

        programStart(false, toExtFlash, withFastMode, dataFile, writeAddress, new EraseFlashProgressListener() {
            @Override
            public void onSectorErased(int erased, int total) {
                if (progressCallback != null) {
                    int percent = 50 * erased / total;
                    progressCallback.onDfuProgress(percent, 0, "Erasing...");
                }
            }
        });

        final int usedProgressPercent;
        if (withFastMode) {
            usedProgressPercent = 50;
        } else {
            usedProgressPercent = 0;
        }

        programFlash(false, toExtFlash, withFastMode, dataFile, writeAddress, new DataProgressListener() {
            int lastReportPercent = -1;

            @Override
            public void onDataProcessed(Object data, int processedBytes, int totalBytes, long intervalTime, long totalTime) {
                if (progressCallback != null) {
                    int percent = usedProgressPercent + (100 - usedProgressPercent) * processedBytes / totalBytes;
                    if (lastReportPercent != percent) {
                        lastReportPercent = percent;
                        progressCallback.onDfuProgress(percent, (int) (processedBytes * 1000 / totalTime), "Programming...");
                    }
                }
            }
        });

        programEnd(false, toExtFlash, withFastMode, dataFile, false);
    }

    //tools
    private void checkOverlapV2(boolean isUpdateFw, boolean toExtFlash, DfuFile dfuFile, int writeAddress, int addressOfSCA, BootInfo appBootloader, BootInfo runningAppFw) throws Throwable {
        // 首先不能覆盖SCA和bootloader
        int writeSize = dfuFile.getData().length;
        // SCA
        if (BootInfo.hasOverlap(writeAddress, writeSize, addressOfSCA, 0x2000)) {
            throw new Error(createOverlapError("DATA", writeAddress, writeSize, "SCA", addressOfSCA, 0x2000));
        }
        // BOOT
        if (appBootloader.hasOverlap(writeAddress, writeSize)) {
            throw new Error(createOverlapError("DATA", writeAddress, writeSize, "BOOTLOADER", appBootloader.loadAddr, appBootloader.binSize));
        }

        if (isUpdateFw) {
            final BootInfo dfuFileBootInfo = dfuFile.getImgInfo().bootInfo;
            if (writeAddress != dfuFileBootInfo.loadAddr) {
                // double bank model
                if (runningAppFw != null && runningAppFw.hasOverlap(writeAddress, writeSize)) {
                    throw new Error(createOverlapError("DATA", writeAddress, writeSize, "APP_OLD", runningAppFw.loadAddr, runningAppFw.binSize));
                }
                if (BootInfo.hasOverlap(writeAddress, writeSize, dfuFileBootInfo.loadAddr, writeSize)) {
                    throw new Error(createOverlapError("DATA", writeAddress, writeSize, "APP_NEW", dfuFileBootInfo.loadAddr, writeSize));
                }

                if (BootInfo.hasOverlap(dfuFileBootInfo.loadAddr, writeSize, addressOfSCA, 0x2000)) {
                    throw new Error(createOverlapError("APP_NEW", dfuFileBootInfo.loadAddr, writeSize, "SCA", addressOfSCA, 0x2000));
                }
                if (BootInfo.hasOverlap(dfuFileBootInfo.loadAddr, writeSize, appBootloader.loadAddr, appBootloader.binSize)) {
                    throw new Error(createOverlapError("APP_NEW", dfuFileBootInfo.loadAddr, writeSize, "BOOTLOADER", appBootloader.loadAddr, appBootloader.binSize));
                }
            }
            // The chip will jump to AppBootloader when upgrading firmware with single bank.
            // else {
            //     if (runningAppFw != null && runningAppFw.hasOverlap(writeAddress, writeSize)) {
            //         throw new Error(createOverlapError("DATA", writeAddress, writeSize, "APP_OLD", runningAppFw.loadAddr, runningAppFw.binSize));
            //     }
            // }
        } else {
            if (!toExtFlash) {
                if (runningAppFw != null && runningAppFw.hasOverlap(writeAddress, writeSize)) {
                    throw new Error(createOverlapError("DATA", writeAddress, writeSize, "APP", runningAppFw.loadAddr, runningAppFw.binSize));
                }
            }
        }
    }

    private void checkOverlapV1(boolean isUpdateFw, boolean toExtFlash, DfuFile dfuFile, int writeAddress, int addressOfSCA, BootInfo runningFw) throws Throwable {
        // 首先不能覆盖SCA和bootloader
        int writeSize = dfuFile.getData().length;
        // SCA
        if (BootInfo.hasOverlap(writeAddress, writeSize, addressOfSCA, 0x2000)) {
            throw new Error(createOverlapError("DATA", writeAddress, writeSize, "SCA", addressOfSCA, 0x2000));
        }
        // can not overlap running FW while using inner flash.
        if (isUpdateFw || !toExtFlash) {
            if (runningFw.hasOverlap(writeAddress, writeSize)) {
                throw new Error(createOverlapError("DATA", writeAddress, writeSize, "APP_OLD", runningFw.loadAddr, runningFw.binSize));
            }
        }

        if (isUpdateFw) {
            final BootInfo dfuFileBootInfo = dfuFile.getImgInfo().bootInfo;
            // copy mode
            if (writeAddress != dfuFileBootInfo.loadAddr) {
                if (BootInfo.hasOverlap(/*copyAddress*/writeAddress, writeSize, dfuFileBootInfo.loadAddr, writeSize)) {
                    throw new Error(createOverlapError("DATA", writeAddress, writeSize, "APP_NEW", dfuFileBootInfo.loadAddr, writeSize));
                }

                if (BootInfo.hasOverlap(dfuFileBootInfo.loadAddr, writeSize, addressOfSCA, 0x2000)) {
                    throw new Error(createOverlapError("APP_NEW", dfuFileBootInfo.loadAddr, writeSize, "SCA", addressOfSCA, 0x2000));
                }
            }
        }
    }

    private String createOverlapError(String srcRegionName, int srcAddr, int srcSize, String dstRegionName, int dstAddr, int dstSize) {
        final StringBuilder builder = new StringBuilder(128);
        builder.append(srcRegionName);
        MiscUtils.appendOverlapInfo(builder, srcAddr, srcSize);
        builder.append(" overlaps ");
        builder.append(dstRegionName);
        MiscUtils.appendOverlapInfo(builder, dstAddr, dstSize);
        return builder.toString();
    }

    public static class CmdOpcode {
        public static final int GET_INFO = 0X01;
        public static final int RESET = 0X02;
        public static final int WRITE_RAM = 0X11;
        public static final int READ_RAM = 0x12;
        public static final int DUMP_FLASH = 0x21;
        public static final int ERASE_FLASH = 0x22;
        public static final int PROGRAM_START = 0x23;
        public static final int PROGRAM_FLASH = 0x24;
        public static final int PROGRAM_END = 0x25;
        public static final int UPDATE_FLASH = 0x26;
        public static final int SYSTEM_CONFIG = 0x27;
        public static final int OPERATION_NVDS = 0x28;
        public static final int RW_EFUSE = 0x29;
        public static final int CONFIG_EXT_FLASH = 0x2A;
        public static final int GET_FLASH_INFO = 0x2B;
        public static final int RW_REG = 0x2C;
        public static final int SET_DFU_MODE = 0X41;
        public static final int GET_FW_INFO = 0X42;
        public static final int PROGRAM_FLASH_FAST = 0xFF;
    }

    public static String changeMacAddress(String address, int delta) {
        if (address == null)
            return "00:00:00:00:00:00";

        final long macValue = BlockingBleUtil.macToValue(address);
        long leastByteOfMac = macValue & 0xFFL;
        long otherByteOfMac = macValue & 0xFFFF_FFFF_FF00L;

        // only change the least significant byte.
        leastByteOfMac = (leastByteOfMac + delta) & 0xFFL;

        final long newMacValue = otherByteOfMac | leastByteOfMac;

        return BlockingBleUtil.valueToMac(newMacValue);
    }

    public static class StartupBootInfo {
        public boolean isEncrypted;
        public BootInfo bootInfo = new BootInfo();
    }

    public static class ImgInfoList {
        public boolean isEncrypted;
        public ArrayList<ImgInfo> imgList;
    }

    public static class AppBootloaderExtraInfo {
        private static final int CURRENT_FW_IS_BOOTLOADER = 0;
        private static final int CURRENT_FW_IS_APP = 1;

        public int position; // CURRENT_FW_IS_APP

        public int recommendSaveAddress;

        public ImgInfo appFwImgInfo;
    }

    public interface EraseFlashProgressListener {
        void onSectorErased(int erased, int total);
    }
}
