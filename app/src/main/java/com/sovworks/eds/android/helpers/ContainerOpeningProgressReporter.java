package com.sovworks.eds.android.helpers;

public interface ContainerOpeningProgressReporter extends ProgressReporter {
    void setContainerFormatName(String name);
    void setIsHidden(boolean isHidden);
    void setCurrentKDFName(String name);
    void setCurrentEncryptionAlgName(String name);
    void reportProgress(int progress);

    @Override
    default void setText(CharSequence text) {}

    @Override
    default void setProgress(int progress) { reportProgress(progress); }

    @Override
    default boolean isCancelled() { return false; }
}
