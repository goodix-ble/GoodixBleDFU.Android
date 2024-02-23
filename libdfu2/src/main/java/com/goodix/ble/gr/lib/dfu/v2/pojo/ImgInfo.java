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
public class ImgInfo {
    public static final int VALID_PATTERN = 0x4744;
    public static final int IMG_INFO_SIZE = 40;

    public int pattern;        // uint16_t: Image info pattern.
    public int version;        // uint16_t: Image version.
    public final BootInfo bootInfo = new BootInfo();    // dfu_boot_info_t: Image boot info.
    public String comments;    // uint8_t[12]: Image comments. */

    public ImgInfo readFromData(HexSerializer data) {
        this.pattern = data.get(2);
        this.version = data.get(2);
        bootInfo.readFromData(data);
        this.comments = data.getCString(12);
        return this;
    }

    public ImgInfo writeToData(HexSerializer data) {
        data.put(2, this.pattern);
        data.put(2, this.version);
        bootInfo.writeToData(data);
        data.putASCII(12, this.comments);
        return this;
    }

    public ImgInfo copy(ImgInfo that) {
        this.pattern = that.pattern;
        this.version = that.version;
        this.comments = that.comments;
        this.bootInfo.copy(that.bootInfo);
        return this;
    }
}
