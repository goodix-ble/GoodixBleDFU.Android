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

import java.nio.charset.Charset;
import java.util.Arrays;

@SuppressWarnings("unused")
public class HexSerializer {
    protected byte[] buffer;
    protected int absPos; // 相对于buffer的绝对位置

    protected int rangeStart;
    protected int rangeEnd;

    protected boolean bigEndian = false; // little endian in default
    protected boolean readonly = false; // only for reading data

    public HexSerializer() {
    }

    public HexSerializer(HexSerializer that) {
        this.buffer = that.buffer;
        this.absPos = that.absPos;
        this.rangeStart = that.rangeStart;
        this.rangeEnd = that.rangeEnd;
        this.bigEndian = that.bigEndian;
    }


    public HexSerializer(int size) {
        if (size < 0) size = 0;
        setBuffer(new byte[size]);
    }

    public HexSerializer(byte[] pdu) {
        setBuffer(pdu);
    }

    /**
     * 设置开始写入的数据的位置
     *
     * @param pos 表示指定范围{@link #setRange(int, int)}内的相对位置。从 0 开始。
     */
    public void setPos(int pos) {
        this.absPos = this.rangeStart + pos;
        if (this.absPos < this.rangeStart) this.absPos = this.rangeStart;
        if (this.absPos > this.rangeEnd) this.absPos = this.rangeEnd;
    }

    public void setRange(int offsetInBuffer, int size) {
        if (this.absPos < offsetInBuffer) this.absPos = offsetInBuffer;

        this.rangeStart = offsetInBuffer;
        this.rangeEnd = offsetInBuffer + size;

        if (this.rangeEnd > this.buffer.length) {
            this.rangeEnd = this.buffer.length;
        }

        if (this.rangeStart > this.rangeEnd) {
            this.rangeStart = this.rangeEnd;
        }
    }

    public void setRangeAll() {
        this.setRange(0, this.buffer.length);
    }

    public void reset() {
        setRangeAll();
        setPos(0);
    }

    public int getPos() {
        return this.absPos - this.rangeStart;
    }

    public int getOffsetInRange() {
        return getPos();
    }

    public int getRangeSize() {
        return this.rangeEnd - this.rangeStart;
    }

    public int getRangePos() {
        return this.rangeStart;
    }

    public int getOffsetInBuffer() {
        return this.absPos;
    }

    public byte[] copyRangeBuffer() {
        byte[] tmp = new byte[getRangeSize()];
        System.arraycopy(buffer, rangeStart, tmp, 0, tmp.length);
        return tmp;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public void setBuffer(byte[] buffer) {
        this.buffer = buffer;
        this.absPos = 0;
        setRange(0, buffer.length);
    }

    /**
     * 获取剩余还未处理的字节数
     */
    public int getRemainSize() {
        return this.rangeEnd - this.absPos;
    }

    public void setEndian(boolean bigEndian) {
        this.bigEndian = bigEndian;
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    public int getChecksum(int pos, int dataSize) {
        final int start = this.rangeStart + pos;

        if (start > this.rangeEnd) {
            return 0;
        }

        if (start + dataSize > this.rangeEnd) {
            dataSize = this.rangeEnd - start;
        }

        return calcChecksum(this.buffer, start, dataSize);
    }

    public static int calcChecksum(byte[] buffer, int offset, int size) {
        int sum = 0;

        if (buffer == null) {
            return 0;
        }

        int endPos = offset + size;

        if (endPos > buffer.length) {
            endPos = buffer.length;
        }

        for (int i = offset; i < endPos; i++) {
            sum += 0xFF & buffer[i];
        }
        return sum;
    }

    /**
     * Ignore N bytes.
     *
     * @param size jump forward if positive integer, and jump backwards if negative integer.
     */
    public void skip(int size) {
        this.absPos += size;
        if (this.absPos < this.rangeStart) this.absPos = this.rangeStart;
        if (this.absPos > this.rangeEnd) this.absPos = this.rangeEnd;
    }


    public HexSerializer put(int size, int val) {
        return put(size, val, bigEndian);
    }

    public HexSerializer put(int size, int val, boolean bigEndian) {
        if (readonly) throw new IllegalStateException("This buffer is readonly.");
        if (absPos + size <= this.rangeEnd) {
            HexEndian.toByte(val, buffer, absPos, size, bigEndian);
            absPos += size;
        } else {
            throw new IllegalStateException("buffer is to small. pos = [" + absPos + "], size = [" + size + "]");
        }
        return this;
    }

    public HexSerializer put(int size, long val) {
        return put(size, val, this.bigEndian);
    }

    public HexSerializer put(int size, long val, boolean bigEndian) {
        if (readonly) throw new IllegalStateException("This buffer is readonly.");
        if (absPos + size <= this.rangeEnd) {
            HexEndian.toByteLong(val, buffer, absPos, size, bigEndian);
            absPos += size;
        } else {
            throw new IllegalStateException("buffer is to small. pos = [" + absPos + "], size = [" + size + "]");
        }
        return this;
    }

    public HexSerializer put(byte[] dat) {
        return put(dat.length, dat, 0);
    }

    public HexSerializer put(int size, byte[] dat, int offset) {
        if (readonly) throw new IllegalStateException("This buffer is readonly.");
        if (dat != null && size > 0) {
            if (this.absPos + size <= this.rangeEnd) {
                System.arraycopy(dat, offset, this.buffer, this.absPos, size);
                this.absPos += size;
            } else {
                throw new IllegalStateException("buffer is to small. pos = [" + absPos + "], size = [" + size + "]");
            }
        }
        return this;
    }

    public HexSerializer putASCII(String ascii) {
        if (ascii != null && !ascii.isEmpty()) {
            return putASCII(ascii.length(), ascii);
        }
        return this;
    }

    public HexSerializer putASCII(int size, String ascii) {
        if (readonly) throw new IllegalStateException("This buffer is readonly.");
        if (size > 0) {
            if (this.absPos + size <= this.rangeEnd) {
                int charCount = 0;
                if (ascii != null) {
                    charCount = ascii.length();
                }
                for (int i = 0; i < size; i++) {
                    if (i < charCount) {
                        this.buffer[this.absPos + i] = (byte) ascii.charAt(i);
                    } else {
                        this.buffer[this.absPos + i] = 0x00;
                    }
                }
                this.absPos += size;
            } else {
                throw new IllegalStateException("buffer is to small. pos = [" + absPos + "], size = [" + size + "]");
            }
        }
        return this;
    }

    public HexSerializer fill(int count, int val) {
        if (readonly) throw new IllegalStateException("This buffer is readonly.");

        int fromIndex = this.absPos;
        int toIndex = fromIndex + count;

        if (toIndex > this.rangeEnd) {
            toIndex = this.rangeEnd;
        }

        this.absPos = toIndex;

        Arrays.fill(this.buffer, fromIndex, toIndex, (byte) val);

        return this;
    }

    public int peek(int pos) {
        pos = pos + rangeStart;
        if (pos < this.rangeEnd) {
            return buffer[pos] & 0xFF;
        }
        return 0;
    }

    public int get(int size) {
        return get(size, bigEndian);
    }

    public int get(int size, boolean bigEndian) {
        if (this.absPos + size > this.rangeEnd) {
            return 0;
        } else {
            int val = HexEndian.fromByte(buffer, absPos, size, bigEndian);
            absPos += size;
            return val;
        }
    }

    public long getLong(int size) {
        return getLong(size, this.bigEndian);
    }

    public long getLong(int size, boolean bigEndian) {
        if (this.absPos + size > this.rangeEnd) {
            return 0;
        } else {
            long val = HexEndian.fromByteLong(buffer, absPos, size, bigEndian);
            absPos += size;
            return val;
        }
    }

    public byte[] getByte(int size) {
        if (size > 0) {
            return getByte(size, new byte[size], 0);
        }
        return new byte[0];
    }

    public byte[] getByte(int size, byte[] out, int offset) {
        if (out != null) {
            if (this.absPos + size > this.rangeEnd) {
                size = this.rangeEnd - this.absPos;
            }

            if (offset + size > out.length) {
                size = out.length - offset;
            }

            System.arraycopy(this.buffer, this.absPos, out, offset, size);
        }
        return out;
    }

    public String getString(Charset charset, int size) {
        if (this.absPos + size > this.rangeEnd) {
            size = this.rangeEnd - this.absPos;
        }

        String s = new String(this.buffer, this.absPos, size, charset);
        this.absPos += size;

        return s;
    }

    public String getCString(int size) {
        if (this.absPos + size > this.rangeEnd) {
            size = this.rangeEnd - this.absPos;
        }

        int actualSize = size; // 默认读取需要的大小。再判断能实际给多少。
        // find null-end
        for (int i = 0; i < size; i++) {
            byte b = this.buffer[this.absPos + i];
            if (b == 0 || b == (byte) 0xFF) {
                actualSize = i;
                break;
            }
        }

        String s = new String(this.buffer, this.absPos, actualSize, Charset.defaultCharset());
        this.absPos += size;

        return s;
    }
}
