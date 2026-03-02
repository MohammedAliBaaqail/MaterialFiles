package com.sovworks.eds.fs;

import java.io.IOException;

public interface DataInput {
    int read() throws IOException;
    int read(byte[] b, int off, int len) throws IOException;
}
