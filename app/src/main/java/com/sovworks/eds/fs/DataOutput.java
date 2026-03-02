package com.sovworks.eds.fs;

import java.io.IOException;

public interface DataOutput {
    void write(int b) throws IOException;
    void write(byte[] b, int off, int len) throws IOException;
    void flush() throws IOException;
}
