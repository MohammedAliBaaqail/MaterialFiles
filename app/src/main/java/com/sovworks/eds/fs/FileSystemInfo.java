package com.sovworks.eds.fs;

import android.os.Parcelable;
import java.io.IOException;

public abstract class FileSystemInfo implements Parcelable {
    public abstract String getFileSystemName();
    public abstract void makeNewFileSystem(RandomAccessIO img) throws IOException;
    public abstract FileSystem openFileSystem(RandomAccessIO img, boolean readOnly) throws IOException;
}
