/*
 * Copyright (C) 2015 The Android Open Source Project
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


package android.hardware.camera2.params;

import android.hardware.camera2.CameraDevice;
import android.view.Surface;

import static com.android.internal.util.Preconditions.*;

/**
 * Immutable class for describing camera output, which contains a {@link Surface} and its specific
 * configuration for creating capture session.
 *
 * @see CameraDevice#createCaptureSession
 *
 * @hide
 */
public final class OutputConfiguration {

    /**
     * Rotation constant: 0 degree rotation (no rotation)
     */
    public static final int ROTATION_0 = 0;

    /**
     * Rotation constant: 90 degree counterclockwise rotation.
     */
    public static final int ROTATION_90 = 1;

    /**
     * Rotation constant: 180 degree counterclockwise rotation.
     */
    public static final int ROTATION_180 = 2;

    /**
     * Rotation constant: 270 degree counterclockwise rotation.
     */
    public static final int ROTATION_270 = 3;

    /**
     * Create a new immutable SurfaceConfiguration instance.
     *
     * @param surface
     *          A Surface for camera to output to.
     *
     * <p>This constructor creates a default configuration</p>
     *
     */
    public OutputConfiguration(Surface surface) {
        checkNotNull(surface, "Surface must not be null");
        mSurface = surface;
        mRotation = ROTATION_0;
    }

    /**
     * Create a new immutable SurfaceConfiguration instance.
     *
     * <p>This constructor takes an argument for desired camera rotation</p>
     *
     * @param surface
     *          A Surface for camera to output to.
     * @param rotation
     *          The desired rotation to be applied on camera output. Value must be one of
     *          ROTATION_[0, 90, 180, 270]. Note that when the rotation is 90 or 270 degree,
     *          application should make sure corresponding surface size has width and height
     *          transposed corresponding to the width and height without rotation. For example,
     *          if application needs camera to capture 1280x720 picture and rotate it by 90 degree,
     *          application should set rotation to {@code ROTATION_90} and make sure the
     *          corresponding Surface size is 720x1280. Note that {@link CameraDevice} might
     *          throw {@code IllegalArgumentException} if device cannot perform such rotation.
     *
     */
    public OutputConfiguration(Surface surface, int rotation) {
        checkNotNull(surface, "Surface must not be null");
        checkArgumentInRange(rotation, ROTATION_0, ROTATION_270, "Rotation constant");
        mSurface = surface;
        mRotation = rotation;
    }

    /**
     * Get the {@link Surface} associated with this {@link OutputConfiguration}.
     *
     * @return the {@link Surface} associated with this {@link OutputConfiguration}.
     */
    public Surface getSurface() {
        return mSurface;
    }

    /**
     * Get the rotation associated with this {@link OutputConfiguration}.
     *
     * @return the rotation associated with this {@link OutputConfiguration}.
     *         Value will be one of ROTATION_[0, 90, 180, 270]
     */
    public int getRotation() {
        return mRotation;
    }

    private final Surface mSurface;
    private final int mRotation;
}
