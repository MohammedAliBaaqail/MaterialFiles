package com.sovworks.eds.crypto.kdf;

import java.security.DigestException;
import java.security.MessageDigest;
import java.util.Arrays;

import com.sovworks.eds.crypto.EncryptionEngineException;

public class HMAC {
    public HMAC(byte[] key, MessageDigest md, int blockSize) {
        _md = md;
        _digestLength = md.getDigestLength();
        _digest = new byte[_digestLength];
        _tmp = new byte[_digestLength];
        _key = key.length > blockSize ? md.digest(key) : key.clone();
        
        _ipad = new byte[blockSize];
        _opad = new byte[blockSize];
        for (int i = 0; i < _key.length; i++) {
            _ipad[i] = (byte) (_key[i] ^ 0x36);
            _opad[i] = (byte) (_key[i] ^ 0x5C);
        }
        Arrays.fill(_ipad, _key.length, blockSize, (byte) 0x36);
        Arrays.fill(_opad, _key.length, blockSize, (byte) 0x5C);
    }

    public int getDigestLength() {
        return _digestLength;
    }

    public void calcHMAC(byte[] data, int dataOffset, int dataLen, byte[] out) throws DigestException, EncryptionEngineException {
        _md.reset();
        _md.update(_ipad);
        _md.update(data, dataOffset, dataLen);
        _md.digest(_tmp, 0, _digestLength);

        _md.reset();
        _md.update(_opad);
        _md.update(_tmp);
        _md.digest(out, 0, _digestLength);
    }

    public void close() {
        _md.reset();
        Arrays.fill(_key, (byte) 0);
        Arrays.fill(_ipad, (byte) 0);
        Arrays.fill(_opad, (byte) 0);
        Arrays.fill(_digest, (byte) 0);
        Arrays.fill(_tmp, (byte) 0);
    }

    protected final MessageDigest _md;
    protected final int _digestLength;
    protected final byte[] _digest, _key, _ipad, _opad, _tmp;
}
