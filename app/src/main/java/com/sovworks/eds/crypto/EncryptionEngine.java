package com.sovworks.eds.crypto;

public interface EncryptionEngine {
    void init() throws EncryptionEngineException;
    void decrypt(byte[] data, int offset, int len) throws EncryptionEngineException;
    void encrypt(byte[] data, int offset, int len) throws EncryptionEngineException;
    void setIV(byte[] iv);
    byte[] getIV();
    int getIVSize();
    void setKey(byte[] key);
    byte[] getKey();
    int getKeySize();
    void close();
    String getCipherName();
    String getCipherModeName();
}
