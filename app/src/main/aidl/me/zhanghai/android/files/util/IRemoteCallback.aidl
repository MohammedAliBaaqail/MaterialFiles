/*
 * Override output path to avoid spaces in paths
 * @hide
 */
package me.zhanghai.android.files.util;

import android.os.Bundle;

interface IRemoteCallback {
    void sendResult(in Bundle result);
}
