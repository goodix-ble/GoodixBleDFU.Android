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

public class HexEndian {

    /**
     * if input is little endian, output is big endian
     * if input is big endian, output is little endian
     */
    public static int changeEndian(int org, int size) {
        int val = 0;
        for (int i = 0; i < size; i++) {
            val <<= 8;
            val |= org & 0xff;
            org >>= 8;
        }

        return val;
    }

    public static int fromByte(byte[] dat, int pos, int size, boolean bigEndian) {
        int val = 0;
        int end = pos + size;
        if (dat != null && pos >= 0 && size >= 0) {
            if (end > dat.length) {
                end = dat.length;
            }

            if (bigEndian) {
                for (int i = pos; i < end; i++) {
                    val <<= 8;
                    val |= (dat[i] & 0xFF);
                }
            } else {
                for (int i = end - 1; i >= pos; i--) {
                    val <<= 8;
                    val |= (dat[i] & 0xFF);
                }
            }
            return val;
        }
        return 0;
    }

    public static byte[] toByte(int val, byte[] out, int pos, int size, boolean bigEndian) {
        int end = pos + size;
        if (out != null && pos >= 0 && size >= 0) {
            if (end > out.length) {
                end = out.length;
            }

            if (bigEndian) {
                for (int i = end - 1; i >= pos; i--) {
                    out[i] = (byte) (val & 0xff);
                    val >>= 8;
                }
            } else {
                for (int i = pos; i < end; i++) {
                    out[i] = (byte) (val & 0xff);
                    val >>= 8;
                }
            }
        }
        return out;
    }

    /**
     * For long integer
     */
    public static long fromByteLong(byte[] dat, int pos, int size, boolean bigEndian) {
        long val = 0;
        int end = pos + size;
        if (dat != null && pos >= 0 && size >= 0) {
            if (end > dat.length) {
                end = dat.length;
            }

            if (bigEndian) {
                for (int i = pos; i < end; i++) {
                    val <<= 8;
                    val |= (dat[i] & 0xFF);
                }
            } else {
                for (int i = end - 1; i >= pos; i--) {
                    val <<= 8;
                    val |= (dat[i] & 0xFF);
                }
            }
            return val;
        }
        return 0;
    }

    public static byte[] toByteLong(long val, byte[] out, int pos, int size, boolean bigEndian) {
        int end = pos + size;
        if (out != null && pos >= 0 && size >= 0) {
            if (end > out.length) {
                end = out.length;
            }

            if (bigEndian) {
                for (int i = end - 1; i >= pos; i--) {
                    out[i] = (byte) (val & 0xff);
                    val >>= 8;
                }
            } else {
                for (int i = pos; i < end; i++) {
                    out[i] = (byte) (val & 0xff);
                    val >>= 8;
                }
            }
        }
        return out;
    }
}
