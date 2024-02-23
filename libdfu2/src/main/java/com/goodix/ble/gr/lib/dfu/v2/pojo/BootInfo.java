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

package com.goodix.ble.gr.lib.dfu.v2.pojo;

import com.goodix.ble.gr.lib.com.HexSerializer;

/**
 * reference "gr55xx_dfu.h"
 */
public class BootInfo {
    public int binSize;        // uint32_t: Firmware Size. */
    public int checksum;       // uint32_t: Firmware Check Sum Value. */
    public int loadAddr;       // uint32_t: Firmware Load Address. */
    public int runAddr;        // uint32_t: Firmware Run Address. */
    public int xqspiXipCmd;    // uint32_t: XIP Read Mode. 0x03: Read mode, 0x0B: Fast Read mode, 0x3B: DualOut Fast Read mode, 0xBB: DualIO Fast Read mode, 0x6B: QuadOut Fast Read mode, 0xEB: QuadIO Fast Read mode */
    public int xqspiSpeed;     // uint32_t:4: Bit: 0-3  clock speed. 0 :64 MHz, 1:48 MHz, 2:32 MHz, 3:24 MHz, 4:16 MHz. */
    public int codeCopyMode;   // uint32_t:1: Bit: 4 code copy mode. 0:XIP,1:QSPI. */
    public int systemClk;      // uint32_t:3: Bit: 5-7 system clock. 0:64 MHz, 1:48 MHz, 2:32 MHz(xo), 3:24 MHz, 4:16 MHz, 5:32 MHz(cpll). */
    public int checkImage;     // uint32_t:1: Bit: 8 check image. */
    public int bootDelay;      // uint32_t:1: Bit: Boot delay flag. */
    public int isDapBoot;      // uint32_t:1: Bit: 11 check if boot dap mode. */
    //public int reserved;       // uint32_t:21: Bit: 24 reserved. */

    public BootInfo readFromData(HexSerializer data) {
        this.binSize = data.get(4);
        this.checksum = data.get(4);
        this.loadAddr = data.get(4);
        this.runAddr = data.get(4);
        this.xqspiXipCmd = data.get(4);

        int config = data.get(4);
        this.xqspiSpeed = config & 0b1111;
        config >>= 4;
        this.codeCopyMode = config & 0b1;
        config >>= 1;
        this.systemClk = config & 0b111;
        config >>= 3;
        this.checkImage = config & 0b1;
        config >>= 1;
        this.bootDelay = config & 0b1;
        config >>= 1;
        this.isDapBoot = config & 0b1;
        return this;
    }

    public BootInfo writeToData(HexSerializer data) {
        data.put(4, this.binSize);
        data.put(4, this.checksum);
        data.put(4, this.loadAddr);
        data.put(4, this.runAddr);
        data.put(4, this.xqspiXipCmd);

        int config = 0;
        config |= this.isDapBoot & 0b1;
        config <<= 1;
        config |= this.bootDelay & 0b1;
        config <<= 1;
        config |= this.checkImage & 0b1;
        config <<= 1;
        config |= this.systemClk & 0b111;
        config <<= 3;
        config |= this.codeCopyMode & 0b1;
        config <<= 1;
        config |= this.xqspiSpeed & 0b1111;
        //config <<= 4;

        data.put(4, config);

        return this;
    }

    public BootInfo copy(BootInfo that) {
        this.binSize = that.binSize;
        this.checksum = that.checksum;
        this.loadAddr = that.loadAddr;
        this.runAddr = that.runAddr;
        this.xqspiXipCmd = that.xqspiXipCmd;
        this.xqspiSpeed = that.xqspiSpeed;
        this.codeCopyMode = that.codeCopyMode;
        this.systemClk = that.systemClk;
        this.checkImage = that.checkImage;
        this.bootDelay = that.bootDelay;
        this.isDapBoot = that.isDapBoot;

        return this;
    }

    public boolean hasOverlap(BootInfo that) {
        return hasOverlap(this.loadAddr, this.binSize, that.loadAddr, that.binSize);
    }

    public boolean hasOverlap(int addr, int size) {
        return hasOverlap(this.loadAddr, this.binSize, addr, size);
    }

    public static boolean hasOverlap(int addr1, int size1, int addr2, int size2) {
        long srcStartL = 0xFFFF_FFFFL & addr1;
        long srcEndL = srcStartL + (0xFFFF_FFFFL & size1);
        long dstStartL = 0xFFFF_FFFFL & addr2;
        long dstEndL = dstStartL + (0xFFFF_FFFFL & size2);
        return srcEndL > dstStartL && srcStartL < dstEndL;
    }
}
