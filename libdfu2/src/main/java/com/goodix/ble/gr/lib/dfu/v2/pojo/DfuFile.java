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

import java.io.IOException;
import java.io.InputStream;

public class DfuFile {
    private byte[] data;
    private ImgInfo imgInfo;
    private String lastError = "success";
    private int fileChecksum;
    private boolean encrypted = false;
    private boolean signed = false;

    public byte[] getData() {
        if (data == null) {
            data = new byte[0];
        }
        return data;
    }

    public ImgInfo getImgInfo() {
        return imgInfo;
    }

    public String getLastError() {
        return lastError;
    }

    public int getFileChecksum() {
        return fileChecksum;
    }

    public boolean isValidDfuFile() {
        return imgInfo != null;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public boolean isSigned() {
        return signed;
    }

    public boolean load(InputStream in, boolean closeStream) {
        if (in == null) {
            lastError = "Input is null";
            return false;
        }

        try {
            int fileSize = in.available();
            if (fileSize > 0) {
                data = new byte[fileSize];
                int readLen = in.read(data);
                if (readLen == fileSize) {
                    return load(data);
                } else {
                    lastError = "Can't load all data from stream";
                }
            } else {
                lastError = "Input size is zero";
            }
        } catch (IOException e) {
            lastError = e.getMessage();
            e.printStackTrace();
        } finally {
            if (closeStream) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    public boolean load(byte[] dat) {
        if (dat == null) {
            lastError = "load(null)";
            return false;
        }

        imgInfo = null; // 最后用于判断是否是有效的DFU文件
        encrypted = false;
        signed = false;

        int fileSize = dat.length;
        if (fileSize > 0) {
            data = dat;
            // 计算校验和
            fileChecksum = 0;
            for (int i = 0; i < fileSize; i++) {
                fileChecksum += 0xFF & data[i];
            }
            // 读取 ImgInfo 先按未加密的读取，不行再按加密的读取
            HexSerializer reader = new HexSerializer(data);
            reader.setPos(fileSize - 48);
            if (reader.get(2) == 0x4744) {
                reader.setPos(fileSize - 48);
            } else {
                reader.setPos(fileSize - 48 - 856);
                if (reader.get(2) == 0x4744) {
                    encrypted = true;
                    signed = true;
                    // 进一步判断是否加密
                    reader.setPos(fileSize - (256 + 520 + 8)); // 定位到reserved区域
                    int rsv = reader.get(4);
                    if (rsv == 0x4E474953) {
                        encrypted = false; // 仅加签未加密
                    }
                    reader.setPos(fileSize - 48 - 856);
                } else {
                    lastError = "Can't find image information data";
                    return false;
                }
            }
            imgInfo = new ImgInfo();
            imgInfo.readFromData(reader);
            return true;
        } else {
            lastError = "Input size is zero";
        }
        return false;
    }
}
