package com.sovworks.eds.crypto;

public interface FileEncryptionEngine extends EncryptionEngine {
    int getFileBlockSize();
    int getEncryptionBlockSize();
    void setIncrementIV(boolean val);
}
