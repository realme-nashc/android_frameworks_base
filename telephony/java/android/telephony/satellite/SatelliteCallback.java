/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.telephony.satellite;

import android.annotation.NonNull;
import android.os.Binder;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;

/**
 * A callback class for monitoring changes in specific satellite service states on the device,
 * including provision state, position update, message transfer state and others.
 * <p>
 * To register a callback, use a {@link SatelliteCallback} which implements the interested
 * interfaces. For example,
 * FakeSatelliteProvisionStateCallback extends {@link SatelliteCallback} implements
 * {@link SatelliteCallback.SatelliteProvisionStateListener}.
 * <p>
 * Then override the methods for the state that you wish to receive updates for, and
 * pass your SatelliteCallback object to the corresponding register function like
 * {@link SatelliteManager#registerForSatelliteProvisionStateChanged}.
 * <p>
 *
 * @hide
 */
public class SatelliteCallback {
    private ISatelliteStateListener mCallbackStub;

    /**
     * The SatelliteCallback needs an executor to execute the callback interfaces.
     */
    public void init(@NonNull Executor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("SatelliteCallback executor must be non-null");
        }
        mCallbackStub = new ISatelliteStateListenerStub(this, executor);
    }

    public ISatelliteStateListener getCallbackStub() {
        return mCallbackStub;
    }

    /**
     * Interface for satellite provision state change listener.
     */
    public interface SatelliteProvisionStateListener {
        /**
         * Called when satellite provision state changes.
         *
         * @param provisioned The new provision state. {@code true} means satellite is provisioned
         *                    {@code false} means satellite is not provisioned.
         */
        void onSatelliteProvisionStateChanged(boolean provisioned);
    }

    /**
     * Interface for position update change listener.
     */
    public interface SatellitePositionUpdateListener {
        /**
         * Called when the satellite position changes.
         *
         * @param pointingInfo The pointing info containing the satellite location.
         */
        void onSatellitePositionUpdate(@NonNull PointingInfo pointingInfo);

        /**
         * Called when satellite message transfer state changes.
         *
         * @param state The new message transfer state.
         */
        void onMessageTransferStateUpdate(
                @SatelliteManager.SatelliteMessageTransferState int state);
    }

    private static class ISatelliteStateListenerStub extends ISatelliteStateListener.Stub {
        private WeakReference<SatelliteCallback> mSatelliteCallbackWeakRef;
        private Executor mExecutor;

        ISatelliteStateListenerStub(SatelliteCallback satelliteCallback, Executor executor) {
            mSatelliteCallbackWeakRef = new WeakReference<>(satelliteCallback);
            mExecutor = executor;
        }

        public void onSatelliteProvisionStateChanged(boolean provisioned) {
            SatelliteProvisionStateListener listener =
                    (SatelliteProvisionStateListener) mSatelliteCallbackWeakRef.get();
            if (listener == null) return;

            Binder.withCleanCallingIdentity(() -> mExecutor.execute(
                    () -> listener.onSatelliteProvisionStateChanged(provisioned)));
        }

        public void onSatellitePositionUpdate(@NonNull PointingInfo pointingInfo) {
            SatellitePositionUpdateListener listener =
                    (SatellitePositionUpdateListener) mSatelliteCallbackWeakRef.get();
            if (listener == null) return;

            Binder.withCleanCallingIdentity(() -> mExecutor.execute(
                    () -> listener.onSatellitePositionUpdate(pointingInfo)));
        }

        public void onMessageTransferStateUpdate(
                @SatelliteManager.SatelliteMessageTransferState int state) {
            SatellitePositionUpdateListener listener =
                    (SatellitePositionUpdateListener) mSatelliteCallbackWeakRef.get();
            if (listener == null) return;

            Binder.withCleanCallingIdentity(() -> mExecutor.execute(
                    () -> listener.onMessageTransferStateUpdate(state)));
        }
    }
}
