/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package me.zhanghai.android.files.util;
public interface IRemoteCallback extends android.os.IInterface
{
  /** Default implementation for IRemoteCallback. */
  public static class Default implements me.zhanghai.android.files.util.IRemoteCallback
  {
    @Override public void sendResult(android.os.Bundle result) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements me.zhanghai.android.files.util.IRemoteCallback
  {
    private static final java.lang.String DESCRIPTOR = "me.zhanghai.android.files.util.IRemoteCallback";
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an me.zhanghai.android.files.util.IRemoteCallback interface,
     * generating a proxy if needed.
     */
    public static me.zhanghai.android.files.util.IRemoteCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof me.zhanghai.android.files.util.IRemoteCallback))) {
        return ((me.zhanghai.android.files.util.IRemoteCallback)iin);
      }
      return new me.zhanghai.android.files.util.IRemoteCallback.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
        case TRANSACTION_sendResult:
        {
          data.enforceInterface(descriptor);
          android.os.Bundle _arg0;
          if ((0!=data.readInt())) {
            _arg0 = android.os.Bundle.CREATOR.createFromParcel(data);
          }
          else {
            _arg0 = null;
          }
          this.sendResult(_arg0);
          return true;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
    }
    private static class Proxy implements me.zhanghai.android.files.util.IRemoteCallback
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public void sendResult(android.os.Bundle result) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if ((result!=null)) {
            _data.writeInt(1);
            result.writeToParcel(_data, 0);
          }
          else {
            _data.writeInt(0);
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_sendResult, _data, null, android.os.IBinder.FLAG_ONEWAY);
          if (!_status && getDefaultImpl() != null) {
            getDefaultImpl().sendResult(result);
            return;
          }
        }
        finally {
          _data.recycle();
        }
      }
      public static me.zhanghai.android.files.util.IRemoteCallback sDefaultImpl;
    }
    static final int TRANSACTION_sendResult = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    public static boolean setDefaultImpl(me.zhanghai.android.files.util.IRemoteCallback impl) {
      // Only one user of this interface can use this function
      // at a time. This is a heuristic to detect if two different
      // users in the same process use this function.
      if (Stub.Proxy.sDefaultImpl != null) {
        throw new IllegalStateException("setDefaultImpl() called twice");
      }
      if (impl != null) {
        Stub.Proxy.sDefaultImpl = impl;
        return true;
      }
      return false;
    }
    public static me.zhanghai.android.files.util.IRemoteCallback getDefaultImpl() {
      return Stub.Proxy.sDefaultImpl;
    }
  }
  public void sendResult(android.os.Bundle result) throws android.os.RemoteException;
} 