package com.sovworks.eds.fs.util;

import android.os.ParcelFileDescriptor;

import com.sovworks.eds.fs.DataInput;
import com.sovworks.eds.fs.DataOutput;
import com.sovworks.eds.fs.File.AccessMode;
import com.sovworks.eds.fs.FileSystem;
import com.sovworks.eds.fs.Path;

import java.io.IOException;

public class Util {
    public static int readBytes(DataInput input, byte[] b, int len) throws IOException {
        int res = 0;
        for (int tmp; res < len; ) {
            tmp = input.read(b, res, len - res);
            if (tmp >= 0)
                res += tmp;
            else
                break;
        }
        return res;
    }

    public static int readBytes(DataInput input, byte[] b) throws IOException {
        return readBytes(input, b, b.length);
    }

    public static int readUnsignedByte(DataInput input) throws IOException {
        return input.read() & 0xFF;
    }

    public static int readWordLE(DataInput input) throws IOException {
        int lo = input.read() & 0xFF;
        int hi = input.read() & 0xFF;
        return (hi << 8) | lo;
    }

    public static long readDoubleWordLE(DataInput input) throws IOException {
        long b0 = input.read() & 0xFF;
        long b1 = input.read() & 0xFF;
        long b2 = input.read() & 0xFF;
        long b3 = input.read() & 0xFF;
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    public static void writeWordLE(DataOutput output, short value) throws IOException {
        output.write(value & 0xFF);
        output.write((value >> 8) & 0xFF);
    }

    public static void writeDoubleWordLE(DataOutput output, int value) throws IOException {
        output.write(value & 0xFF);
        output.write((value >> 8) & 0xFF);
        output.write((value >> 16) & 0xFF);
        output.write((value >> 24) & 0xFF);
    }

    public static void copyFileToOutputStream(java.io.OutputStream output,
            com.sovworks.eds.fs.File file, long offset, long count,
            com.sovworks.eds.fs.File.ProgressInfo progressInfo) throws IOException {
        com.sovworks.eds.fs.RandomAccessIO io = file.getRandomAccessIO(com.sovworks.eds.fs.File.AccessMode.Read);
        try {
            io.seek(offset);
            byte[] buf = new byte[65536];
            long remaining = count;
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int read = io.read(buf, 0, toRead);
                if (read <= 0) break;
                output.write(buf, 0, read);
                remaining -= read;
                if (progressInfo != null && progressInfo.isCancelled()) break;
            }
        } finally {
            io.close();
        }
    }

    public static void copyFileFromInputStream(java.io.InputStream input,
            com.sovworks.eds.fs.File file, long offset, long count,
            com.sovworks.eds.fs.File.ProgressInfo progressInfo) throws IOException {
        com.sovworks.eds.fs.RandomAccessIO io = file.getRandomAccessIO(com.sovworks.eds.fs.File.AccessMode.ReadWrite);
        try {
            io.seek(offset);
            byte[] buf = new byte[65536];
            long remaining = count;
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int read = input.read(buf, 0, toRead);
                if (read <= 0) break;
                io.write(buf, 0, read);
                remaining -= read;
                if (progressInfo != null && progressInfo.isCancelled()) break;
            }
        } finally {
            io.close();
        }
    }

    public static int unsignedByteToInt(byte b) {
        return b & 0xFF;
    }

    public static int unsignedShortToIntLE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF) | ((buf[offset + 1] & 0xFF) << 8);
    }

    public static long unsignedIntToLongLE(byte[] buf, int offset) {
        return ((long)(buf[offset] & 0xFF))
             | ((long)(buf[offset + 1] & 0xFF) << 8)
             | ((long)(buf[offset + 2] & 0xFF) << 16)
             | ((long)(buf[offset + 3] & 0xFF) << 24);
    }

    public static void shortToBytesLE(short value, byte[] buf, int offset) {
        buf[offset]     = (byte)(value & 0xFF);
        buf[offset + 1] = (byte)((value >> 8) & 0xFF);
    }

    public static void intToBytesLE(int value, byte[] buf, int offset) {
        buf[offset]     = (byte)(value & 0xFF);
        buf[offset + 1] = (byte)((value >> 8) & 0xFF);
        buf[offset + 2] = (byte)((value >> 16) & 0xFF);
        buf[offset + 3] = (byte)((value >> 24) & 0xFF);
    }

    public static Path makePath(FileSystem fs, Object... elements) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Object el : elements) {
            if (el == null) continue;
            String s = el.toString();
            if (s.isEmpty()) continue;
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != java.io.File.separatorChar
                    && s.charAt(0) != java.io.File.separatorChar)
                sb.append(java.io.File.separatorChar);
            sb.append(s);
        }
        return fs.getPath(sb.toString());
    }

    public static int getParcelFileDescriptorModeFromAccessMode(AccessMode accessMode) {
        switch (accessMode) {
            case Read:       return ParcelFileDescriptor.MODE_READ_ONLY;
            case Write:
            case ReadWrite:  return ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE;
            case ReadWriteTruncate:
            default:         return ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
        }
    }
}
