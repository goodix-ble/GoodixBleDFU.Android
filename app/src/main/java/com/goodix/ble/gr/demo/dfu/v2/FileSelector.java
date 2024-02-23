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

package com.goodix.ble.gr.demo.dfu.v2;

import android.net.Uri;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class FileSelector implements ActivityResultCallback<Uri> {
    public interface ResultListener {
        void onFileSelected(FileSelector selector, Uri fileUri, String fileName);
    }

    private String mimeType = "*/*";
    private final ActivityResultLauncher<String> fileSelectorLauncher;
    private final ComponentActivity host;
    private ResultListener listener = null;

    @Nullable
    public Uri selectedFileUri = null;
    @NonNull
    public String selectedFileName = "N/A";
    public long selectedFileSize = 0;

    public FileSelector(ComponentActivity host) {
        this.host = host;
        fileSelectorLauncher = host.registerForActivityResult(new ActivityResultContracts.GetContent(), this);
    }

    public void show() {
        fileSelectorLauncher.launch(mimeType);
    }

    public void show(ResultListener listener) {
        this.listener = listener;
        fileSelectorLauncher.launch(mimeType);
    }

    public void setListener(ResultListener listener) {
        this.listener = listener;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    @Nullable
    public InputStream openInputStream() {
        final Uri uri = selectedFileUri;
        if (uri != null) {
            try {
                return this.host.getContentResolver().openInputStream(uri);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Nullable
    public byte[] getData() {
        InputStream inputStream = null;
        try {
            inputStream = this.host.getContentResolver().openInputStream(selectedFileUri);
            final int fileSize = inputStream.available();
            if (fileSize > 0) {
                final byte[] buf = new byte[fileSize];
                int pos = 0;
                while (pos < fileSize) {
                    int readSize = inputStream.read(buf, pos, fileSize - pos);
                    if (readSize < 1) {
                        break;
                    }
                    pos += readSize;
                }
                if (pos != fileSize) {
                    return Arrays.copyOf(buf, pos);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    @Override
    public void onActivityResult(Uri result) {
        if (result != null) {
            selectedFileUri = result;
            final DocumentFile docFile = DocumentFile.fromSingleUri(this.host, result);
            if (docFile != null) {
                final String name = docFile.getName();
                if (name == null) {
                    selectedFileName = "N/A";
                } else {
                    selectedFileName = name;
                }

                selectedFileSize = docFile.length();
            } else {
                selectedFileName = "N/A";
            }

            // notify
            final ResultListener listener = this.listener;
            if (listener != null) {
                listener.onFileSelected(this, result, selectedFileName);
            }
        }
    }
}
