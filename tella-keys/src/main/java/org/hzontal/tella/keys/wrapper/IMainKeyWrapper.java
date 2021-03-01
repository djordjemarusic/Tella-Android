package org.hzontal.tella.keys.wrapper;

import org.hzontal.tella.keys.key.MainKey;
import org.hzontal.tella.keys.key.WrappedMainKey;

import java.security.spec.KeySpec;

import javax.crypto.SecretKey;

public interface IMainKeyWrapper {
    String getName();

    void wrap(MainKey mainKey, KeySpec keySpec, IWrapCallback callback); // todo: KeySpec not Key
    void unwrap(WrappedMainKey mainKey, KeySpec keySpec, IUnwrapCallback callback);

    interface IWrapCallback {
        void onReady(WrappedMainKey wrappedMainKey);
        void onError(Throwable error);
    }

    interface IUnwrapCallback {
        void onReady(MainKey mainKey);
        void onError(Throwable error);
    }
}
