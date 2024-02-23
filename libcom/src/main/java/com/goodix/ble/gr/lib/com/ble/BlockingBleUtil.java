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

package com.goodix.ble.gr.lib.com.ble;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class BlockingBleUtil {
    public static char[] HEX_ALPHABET = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private static final String[] requiredPermissions = new String[4];

    public static boolean checkPermission(Context appCtx,
                                          @Nullable ActivityResultLauncher<String[]> permissionsLauncher) {
        return checkPermission(appCtx, true, true, permissionsLauncher);
    }

    public static boolean checkPermission(Context appCtx,
                                          boolean permissionForScanning,
                                          boolean permissionForConnection,
                                          @Nullable ActivityResultLauncher<String[]> permissionsLauncher) {
        if (appCtx == null) {
            return false;
        }

        // List
        int requiredPermissionCnt = 0;
        int absentPermissionCnt = 0;

        if (permissionForScanning) {
            requiredPermissions[requiredPermissionCnt++] = Manifest.permission.ACCESS_COARSE_LOCATION;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requiredPermissions[requiredPermissionCnt++] = Manifest.permission.ACCESS_FINE_LOCATION;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requiredPermissions[requiredPermissionCnt++] = Manifest.permission.BLUETOOTH_SCAN;
            }
        }
        if (permissionForConnection) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requiredPermissions[requiredPermissionCnt++] = Manifest.permission.BLUETOOTH_CONNECT;
            }
        }

        // Check
        for (int i = 0; i < requiredPermissionCnt; i++) {
            String permission = requiredPermissions[i];
            if (ContextCompat.checkSelfPermission(appCtx, permission) == PackageManager.PERMISSION_GRANTED) {
                requiredPermissions[i] = null;
            } else {
                absentPermissionCnt++;
            }
        }

        // Request
        if (absentPermissionCnt > 0 && permissionsLauncher != null) {

            String[] absentPermissions = new String[absentPermissionCnt];
            int pos = 0;
            for (int i = 0; i < requiredPermissionCnt; i++) {
                String permission = requiredPermissions[i];
                if (permission != null) {
                    absentPermissions[pos++] = permission;
                }
            }
            permissionsLauncher.launch(absentPermissions);
        }
        return absentPermissionCnt == 0;
    }

    public static long macToValue(CharSequence mac) {
        long val = 0;
        if (mac != null) {
            for (int i = 0; i < mac.length(); i++) {
                final char ch = mac.charAt(i);
                if (ch >= '0' && ch <= '9') {
                    val <<= 4;
                    val |= ((ch - '0') & 0xFL);
                } else if (ch >= 'A' && ch <= 'F') {
                    val <<= 4;
                    val |= (((ch - 'A') + 10) & 0xFL);
                } else if (ch >= 'a' && ch <= 'f') {
                    val <<= 4;
                    val |= (((ch - 'a') + 10) & 0xFL);
                }
            }
        }
        return val;
    }

    public static String valueToMac(long mac) {
        final StringBuilder builder = new StringBuilder(6 * 2 + 5);
        int i = 0;
        while (true) {
            int b = (int) ((mac >> 40) & 0xFFL);
            i++;
            mac <<= 8;
            builder.append(HEX_ALPHABET[b >> 4])    // msb
                    .append(HEX_ALPHABET[b & 0xF]); // lsb
            if (i < 6) {
                builder.append(':');
                continue;
            }
            break;
        }
        return builder.toString();
    }
}
