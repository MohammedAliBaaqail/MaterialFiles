package com.sovworks.eds.android.settings;

import java.io.File;
import me.zhanghai.android.files.app.AppProviderKt;

public class SystemConfig {
    private static SystemConfig sInstance;

    public static SystemConfig getInstance() {
        if (sInstance == null) {
            sInstance = new SystemConfig();
        }
        return sInstance;
    }

    public File getFSMFolderPath() {
        return new File(AppProviderKt.getApplication().getFilesDir(), "fsm");
    }
}
