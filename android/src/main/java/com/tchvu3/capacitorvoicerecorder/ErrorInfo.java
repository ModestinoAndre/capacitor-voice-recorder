package com.tchvu3.capacitorvoicerecorder;

import androidx.annotation.NonNull;

public class ErrorInfo {

    private final int what;
    private final int extra;

    public ErrorInfo(int what, int extra) {
        this.what = what;
        this.extra = extra;
    }

    @NonNull
    @Override
    public String toString() {
        return "what: " + what + " extra: " + extra;
    }
}
