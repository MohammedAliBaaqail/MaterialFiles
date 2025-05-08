/*
 * Generated AIDL interface for remote callbacks
 */
package me.zhanghai.android.files.util;

import android.os.Bundle;

// Adding oneway for efficient one-way IPC calls
oneway interface IRemoteCallback {
    void sendResult(in Bundle result);
}
