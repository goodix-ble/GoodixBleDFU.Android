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

import androidx.annotation.NonNull;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class HexString {
    public final static char[] HEX_CHAR = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    public final static String STR_FOR_NULL = "null";

    public static String toHexString(byte[] dat) {
        if (dat == null) {
            return STR_FOR_NULL;
        }
        return toHexString(dat, 0, dat.length, null);
    }

    public static String toHexString(byte[] dat, int offset, int size) {
        return toHexString(dat, offset, size, null);
    }

    public static String toHexString(byte[] dat, int offset, int size, String byteSeparator) {
        if (dat == null) {
            return STR_FOR_NULL;
        }

        if (size < 1) {
            return "";
        }

        int byteSeparatorLen = 0;
        if (byteSeparator != null) {
            byteSeparatorLen = byteSeparator.length();
        }

        final StringBuilder builder = new StringBuilder(size * (2 + byteSeparatorLen));

        return toHexString(dat, offset, size, byteSeparator, builder).toString();
    }

    public static StringBuilder toHexString(byte[] dat, int offset, int size, String byteSeparator, StringBuilder out) {
        if (out == null) {
            return null;
        }

        if (size < 1) {
            return out;
        }

        if (dat == null) {
            out.append(STR_FOR_NULL);
            return out;
        }

        int startPos = offset;
        if (startPos < 0) {
            startPos = dat.length + offset;
            if (startPos < 0) {
                startPos = 0;
            }
        } else if (startPos > dat.length) {
            return out;
        }

        int endPos = startPos + size;
        if (endPos > dat.length) {
            endPos = dat.length;
        }

        int byteSeparatorLen = 0;
        if (byteSeparator != null) {
            byteSeparatorLen = byteSeparator.length();
        }

        out.ensureCapacity(out.length() + (endPos - startPos) * (2 + byteSeparatorLen));

        int cur = startPos;
        while (true) {
            int v = dat[cur];
            out.append(HEX_CHAR[(v >> 4) & 0xF]);
            out.append(HEX_CHAR[v & 0xF]);

            cur++;
            if (cur < endPos) {
                if (byteSeparatorLen > 0) {
                    out.append(byteSeparator);
                }
            } else {
                break;
            }
        }

        return out;
    }

    public static String toHexString(long val, int sizeOfVal) {
        if (sizeOfVal < 1) {
            return "";
        }
        final StringBuilder builder = new StringBuilder(2 + sizeOfVal * 2);
        builder.append("0x");
        return toHexString(val, sizeOfVal, true, null, builder).toString();
    }

    public static StringBuilder toHexString(long val, int sizeOfVal, boolean bigEndian, String byteSeparator, StringBuilder out) {
        if (out == null) {
            return null;
        }

        if (sizeOfVal < 1) {
            return out;
        }

        int byteSeparatorLen = 0;
        if (byteSeparator != null) {
            byteSeparatorLen = byteSeparator.length();
        }

        out.ensureCapacity(out.length() + sizeOfVal * (2 + byteSeparatorLen));

        long littleEndianVal;
        if (bigEndian) {
            littleEndianVal = 0;
            for (int i = 0; i < sizeOfVal; i++) {
                littleEndianVal <<= 8;
                littleEndianVal |= val & 0xFFL;
                val >>= 8;
            }
        } else {
            littleEndianVal = val;
        }

        for (int i = 0; ; ) {
            int byteVal = (int) (littleEndianVal & 0xFFL);
            littleEndianVal >>= 8;

            out.append(HEX_CHAR[(byteVal >> 4) & 0xF]);
            out.append(HEX_CHAR[byteVal & 0xF]);

            i++;
            if (i < sizeOfVal) {
                if (byteSeparatorLen > 0) {
                    out.append(byteSeparator);
                }
            } else {
                break;
            }
        }

        return out;
    }

    public static String dump(byte[] dat) {
        if (dat == null) {
            return STR_FOR_NULL;
        }

        final StringBuilder builder = new StringBuilder(64 + dat.length * 2);

        return dump(dat, 0, dat.length, null, builder).toString();
    }

    public static String dump(byte[] dat, int offset, int size) {
        if (dat == null) {
            return STR_FOR_NULL;
        }

        if (size < 1) {
            return "[0]";
        }

        final StringBuilder builder = new StringBuilder(64 + size * 2);

        return dump(dat, offset, size, null, builder).toString();
    }

    public static StringBuilder dump(byte[] dat, int offset, int size, String byteSeparator, StringBuilder out) {
        if (out == null) {
            return null;
        }

        if (dat == null) {
            out.append(STR_FOR_NULL);
            return out;
        }

        out.append("[").append(size).append("] ");

        if (size < 1) {
            return out;
        }

        return toHexString(dat, offset, size, byteSeparator, out);
    }

    public final StringBuilder innerBuilder;

    public HexString() {
        this.innerBuilder = new StringBuilder(64);
    }

    public HexString(int capacity) {
        this.innerBuilder = new StringBuilder(capacity);
    }

    public HexString appendHex(byte value) {
        toHexString(value, 1, true, null, this.innerBuilder);
        return this;
    }

    public HexString appendHex(short value) {
        toHexString(value, 2, true, null, this.innerBuilder);
        return this;
    }

    public HexString appendHex(int value) {
        toHexString(value, 4, true, null, this.innerBuilder);
        return this;
    }

    public HexString appendHex(long value) {
        toHexString(value, 8, true, null, this.innerBuilder);
        return this;
    }

    public HexString appendHex(long value, int size) {
        toHexString(value, size, true, null, this.innerBuilder);
        return this;
    }

    public HexString appendHex(byte[] dat) {
        if (dat != null) {
            toHexString(dat, 0, dat.length, null, this.innerBuilder);
        } else {
            this.innerBuilder.append("null");
        }
        return this;
    }

    /**
     * Support negative offset and size.
     */
    public HexString appendHex(byte[] dat, int offset, int size) {
        if (dat != null) {
            if (offset < 0) {
                offset += dat.length;
                if (offset < 0) {
                    offset = 0;
                }
            }
            if (size < 0) {
                size += dat.length;
                if (size < 0) {
                    size = 0;
                }
            }
            if (size > 0) {
                if (offset + size > dat.length) {
                    size = dat.length - offset;
                }
                toHexString(dat, offset, size, null, this.innerBuilder);
            }
        } else {
            this.innerBuilder.append("null");
        }
        return this;
    }

    public HexString append(Object obj) {
        return append(String.valueOf(obj));
    }

    public HexString append(String str) {
        this.innerBuilder.append(str);
        return this;
    }

    public HexString append(StringBuffer sb) {
        this.innerBuilder.append(sb);
        return this;
    }

    public HexString append(CharSequence s) {
        this.innerBuilder.append(s);
        return this;
    }

    public HexString append(boolean b) {
        this.innerBuilder.append(b);
        return this;
    }

    public HexString append(char c) {
        this.innerBuilder.append(c);
        return this;
    }

    public HexString append(int i) {
        this.innerBuilder.append(i);
        return this;
    }

    public HexString append(long lng) {
        this.innerBuilder.append(lng);
        return this;
    }

    public HexString append(float f) {
        this.innerBuilder.append(f);
        return this;
    }

    public HexString append(double d) {
        this.innerBuilder.append(d);
        return this;
    }

    @NonNull
    @Override
    public String toString() {
        return this.innerBuilder.toString();
    }
}
