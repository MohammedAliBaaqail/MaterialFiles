package com.sovworks.eds.veracrypt;

import com.sovworks.eds.truecrypt.StdLayout;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class VolumeLayout extends StdLayout {
    public static int getKDFIterationsFromPIM(int pim) {
        return 15000 + pim * 1000;
    }

    @Override
    public void setNumKDFIterations(int num) {
        _numIterations = num;
    }

    @Override
    public void close() throws IOException {
        super.close();
        _numIterations = 0;
    }

    @Override
    public List<MessageDigest> getSupportedHashFuncs() {
        ArrayList<MessageDigest> l = new ArrayList<>();
        try {
            l.add(MessageDigest.getInstance("SHA-512"));
            l.add(MessageDigest.getInstance("SHA-256"));
            l.add(new com.sovworks.eds.crypto.hash.Whirlpool());
            l.add(new com.sovworks.eds.crypto.hash.RIPEMD160());
        } catch (NoSuchAlgorithmException ignored) {
        }
        return l;
    }

    protected static final byte[] SIG = {'V', 'E', 'R', 'A'};
    protected static final short COMPATIBLE_PROGRAM_VERSION = 0x010b;

    @Override
    protected byte[] getHeaderSignature() {
        return SIG;
    }

    @Override
    protected int getMKKDFNumIterations(MessageDigest hashFunc) {
        if (_numIterations > 0)
             return getKDFIterationsFromPIM(_numIterations);
             
        String alg = hashFunc.getAlgorithm().toLowerCase();
        if (alg.contains("sha512") || alg.contains("sha-512"))
            return 500000;
        if (alg.contains("sha256") || alg.contains("sha-256"))
            return 200000;
        if (alg.contains("whirlpool"))
            return 500000;
        if (alg.contains("ripemd160"))
            return 655331;
            
        return 500000;
    }

    @Override
    protected short getMinCompatibleProgramVersion() {
        return COMPATIBLE_PROGRAM_VERSION;
    }

    private int _numIterations;
}
