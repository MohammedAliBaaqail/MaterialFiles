package com.sovworks.eds.exceptions;

public class ApplicationException extends Exception {
    public static final int CODE_CONTAINER_IS_SYNCING = 1;
    public static final int CODE_CONTAINER_MOUNT_FAILED = 2;

    private static final long serialVersionUID = 1L;
    private int _code;

    public ApplicationException() {}

    public ApplicationException(String msg) {
        super(msg);
    }

    public ApplicationException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public int getCode() {
        return _code;
    }

    public void setCode(int code) {
        _code = code;
    }
}
