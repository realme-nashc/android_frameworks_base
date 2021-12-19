/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import static android.app.TaskInfo.CAMERA_COMPAT_CONTROL_DISMISSED;
import static android.app.TaskInfo.CAMERA_COMPAT_CONTROL_HIDDEN;
import static android.app.TaskInfo.CAMERA_COMPAT_CONTROL_TREATMENT_APPLIED;
import static android.app.TaskInfo.CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.annotation.Nullable;
import android.app.TaskInfo.CameraCompatControlState;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Binder;
import android.util.Log;
import android.view.IWindow;
import android.view.LayoutInflater;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceSession;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowlessWindowManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.SyncTransactionQueue;

/**
 * Holds view hierarchy of a root surface and helps to inflate and manage layout for compat
 * controls.
 */
class CompatUIWindowManager extends WindowlessWindowManager {

    private static final String TAG = "CompatUIWindowManager";

    private final SyncTransactionQueue mSyncQueue;
    private final CompatUIController.CompatUICallback mCallback;
    private final int mDisplayId;
    private final int mTaskId;
    private final Rect mStableBounds;

    private Context mContext;
    private Configuration mTaskConfig;
    private ShellTaskOrganizer.TaskListener mTaskListener;
    private DisplayLayout mDisplayLayout;

    // Remember the last reported states in case visibility changes due to keyguard or
    // IME updates.
    @VisibleForTesting
    boolean mHasSizeCompat;
    @CameraCompatControlState
    private int mCameraCompatControlState = CAMERA_COMPAT_CONTROL_HIDDEN;

    @VisibleForTesting
    boolean mShouldShowSizeCompatHint;
    @VisibleForTesting
    boolean mShouldShowCameraCompatHint;

    @Nullable
    @VisibleForTesting
    CompatUILayout mCompatUILayout;

    @Nullable
    private SurfaceControlViewHost mViewHost;
    @Nullable
    private SurfaceControl mLeash;

    CompatUIWindowManager(Context context, Configuration taskConfig,
            SyncTransactionQueue syncQueue, CompatUIController.CompatUICallback callback,
            int taskId, ShellTaskOrganizer.TaskListener taskListener, DisplayLayout displayLayout,
             boolean hasShownSizeCompatHint, boolean hasShownCameraCompatHint) {
        super(taskConfig, null /* rootSurface */, null /* hostInputToken */);
        mContext = context;
        mSyncQueue = syncQueue;
        mCallback = callback;
        mTaskConfig = taskConfig;
        mDisplayId = mContext.getDisplayId();
        mTaskId = taskId;
        mTaskListener = taskListener;
        mDisplayLayout = displayLayout;
        mShouldShowSizeCompatHint = !hasShownSizeCompatHint;
        mShouldShowCameraCompatHint = !hasShownCameraCompatHint;
        mStableBounds = new Rect();
        mDisplayLayout.getStableBounds(mStableBounds);
    }

    @Override
    public void setConfiguration(Configuration configuration) {
        super.setConfiguration(configuration);
        mContext = mContext.createConfigurationContext(configuration);
    }

    @Override
    protected void attachToParentSurface(IWindow window, SurfaceControl.Builder b) {
        // Can't set position for the ViewRootImpl SC directly. Create a leash to manipulate later.
        final SurfaceControl.Builder builder = new SurfaceControl.Builder(new SurfaceSession())
                .setContainerLayer()
                .setName("CompatUILeash")
                .setHidden(false)
                .setCallsite("CompatUIWindowManager#attachToParentSurface");
        attachToParentSurface(builder);
        mLeash = builder.build();
        b.setParent(mLeash);
    }

    /** Creates the layout for compat controls. */
    void createLayout(boolean show, boolean hasSizeCompat,
            @CameraCompatControlState int cameraCompatControlState) {
        mHasSizeCompat = hasSizeCompat;
        mCameraCompatControlState = cameraCompatControlState;
        if (!show || mCompatUILayout != null) {
            // Wait until compat controls should be visible.
            return;
        }

        initCompatUi();
        updateSurfacePosition();

        if (hasSizeCompat) {
            mCallback.onSizeCompatRestartButtonAppeared(mTaskId);
        }
    }

    private void createLayout(boolean show) {
        createLayout(show, mHasSizeCompat, mCameraCompatControlState);
    }

    /** Called when compat info changed. */
    void updateCompatInfo(Configuration taskConfig,
            ShellTaskOrganizer.TaskListener taskListener, boolean show, boolean hasSizeCompat,
            @CameraCompatControlState int cameraCompatControlState) {
        final Configuration prevTaskConfig = mTaskConfig;
        final ShellTaskOrganizer.TaskListener prevTaskListener = mTaskListener;
        mTaskConfig = taskConfig;
        mTaskListener = taskListener;
        final boolean prevHasSizeCompat = mHasSizeCompat;
        final int prevCameraCompatControlState = mCameraCompatControlState;
        mHasSizeCompat = hasSizeCompat;
        mCameraCompatControlState = cameraCompatControlState;

        // Update configuration.
        mContext = mContext.createConfigurationContext(taskConfig);
        setConfiguration(taskConfig);

        if (mCompatUILayout == null || prevTaskListener != taskListener) {
            // TaskListener changed, recreate the layout for new surface parent.
            release();
            createLayout(show);
            return;
        }

        if (prevHasSizeCompat != mHasSizeCompat
                || prevCameraCompatControlState != mCameraCompatControlState) {
            updateVisibilityOfViews();
        }

        if (!taskConfig.windowConfiguration.getBounds()
                .equals(prevTaskConfig.windowConfiguration.getBounds())) {
            // Reposition the UI surfaces.
            updateSurfacePosition();
        }

        if (taskConfig.getLayoutDirection() != prevTaskConfig.getLayoutDirection()) {
            // Update layout for RTL.
            mCompatUILayout.setLayoutDirection(taskConfig.getLayoutDirection());
            updateSurfacePosition();
        }

    }

    /** Called when the visibility of the UI should change. */
    void updateVisibility(boolean show) {
        if (mCompatUILayout == null) {
            // Layout may not have been created because it was hidden previously.
            createLayout(show);
            return;
        }

        // Hide compat UIs when IME is showing.
        final int newVisibility = show ? View.VISIBLE : View.GONE;
        if (mCompatUILayout.getVisibility() != newVisibility) {
            mCompatUILayout.setVisibility(newVisibility);
        }
    }

    /** Called when display layout changed. */
    void updateDisplayLayout(DisplayLayout displayLayout) {
        final Rect prevStableBounds = mStableBounds;
        final Rect curStableBounds = new Rect();
        displayLayout.getStableBounds(curStableBounds);
        mDisplayLayout = displayLayout;
        if (!prevStableBounds.equals(curStableBounds)) {
            // Stable bounds changed, update UI surface positions.
            updateSurfacePosition();
            mStableBounds.set(curStableBounds);
        }
    }

    /** Called when it is ready to be placed compat UI surface. */
    void attachToParentSurface(SurfaceControl.Builder b) {
        mTaskListener.attachChildSurfaceToTask(mTaskId, b);
    }

    /** Called when the restart button is clicked. */
    void onRestartButtonClicked() {
        mCallback.onSizeCompatRestartButtonClicked(mTaskId);
    }

    /** Called when the camera treatment button is clicked. */
    void onCameraTreatmentButtonClicked() {
        if (!shouldShowCameraControl()) {
            Log.w(TAG, "Camera compat shouldn't receive clicks in the hidden state.");
            return;
        }
        // When a camera control is shown, only two states are allowed: "treament applied" and
        // "treatment suggested". Clicks on the conrol's treatment button toggle between these
        // two states.
        mCameraCompatControlState =
                mCameraCompatControlState == CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED
                        ? CAMERA_COMPAT_CONTROL_TREATMENT_APPLIED
                        : CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED;
        mCallback.onCameraControlStateUpdated(mTaskId, mCameraCompatControlState);
        mCompatUILayout.updateCameraTreatmentButton(mCameraCompatControlState);
    }

    /** Called when the camera dismiss button is clicked. */
    void onCameraDismissButtonClicked() {
        if (!shouldShowCameraControl()) {
            Log.w(TAG, "Camera compat shouldn't receive clicks in the hidden state.");
            return;
        }
        mCameraCompatControlState = CAMERA_COMPAT_CONTROL_DISMISSED;
        mCallback.onCameraControlStateUpdated(mTaskId, CAMERA_COMPAT_CONTROL_DISMISSED);
        mCompatUILayout.setCameraControlVisibility(/* show= */ false);
    }

    /** Called when the restart button is long clicked. */
    void onRestartButtonLongClicked() {
        if (mCompatUILayout == null) {
            return;
        }
        mCompatUILayout.setSizeCompatHintVisibility(/* show= */ true);
    }

    /** Called when either dismiss or treatment camera buttons is long clicked. */
    void onCameraButtonLongClicked() {
        if (mCompatUILayout == null) {
            return;
        }
        mCompatUILayout.setCameraCompatHintVisibility(/* show= */ true);
    }

    int getDisplayId() {
        return mDisplayId;
    }

    int getTaskId() {
        return mTaskId;
    }

    /** Releases the surface control and tears down the view hierarchy. */
    void release() {
        // Hiding before releasing to avoid flickering when transitioning to the Home screen.
        mCompatUILayout.setVisibility(View.GONE);
        mCompatUILayout = null;

        if (mViewHost != null) {
            mViewHost.release();
            mViewHost = null;
        }

        if (mLeash != null) {
            final SurfaceControl leash = mLeash;
            mSyncQueue.runInSync(t -> t.remove(leash));
            mLeash = null;
        }
    }

    void relayout() {
        mViewHost.relayout(getWindowLayoutParams());
        updateSurfacePosition();
    }

    @VisibleForTesting
    void updateSurfacePosition() {
        if (mCompatUILayout == null || mLeash == null) {
            return;
        }

        // Use stable bounds to prevent controls from overlapping with system bars.
        final Rect taskBounds = mTaskConfig.windowConfiguration.getBounds();
        final Rect stableBounds = new Rect();
        mDisplayLayout.getStableBounds(stableBounds);
        stableBounds.intersect(taskBounds);

        // Position of the button in the container coordinate.
        final int positionX = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                ? stableBounds.left - taskBounds.left
                : stableBounds.right - taskBounds.left - mCompatUILayout.getMeasuredWidth();
        final int positionY = stableBounds.bottom - taskBounds.top
                - mCompatUILayout.getMeasuredHeight();

        updateSurfacePosition(positionX, positionY);
    }

    private int getLayoutDirection() {
        return mContext.getResources().getConfiguration().getLayoutDirection();
    }

    private void updateSurfacePosition(int positionX, int positionY) {
        mSyncQueue.runInSync(t -> {
            if (mLeash == null || !mLeash.isValid()) {
                Log.w(TAG, "The leash has been released.");
                return;
            }
            t.setPosition(mLeash, positionX, positionY);
            // The compat UI should be the topmost child of the Task in case there can be more
            // than one children.
            t.setLayer(mLeash, Integer.MAX_VALUE);
        });
    }

    /** Inflates {@link CompatUILayout} on to the root surface. */
    private void initCompatUi() {
        if (mViewHost != null) {
            throw new IllegalStateException(
                    "A UI has already been created with this window manager.");
        }

        // Construction extracted into the separate methods to allow injection for tests.
        mViewHost = createSurfaceViewHost();
        mCompatUILayout = inflateCompatUILayout();
        mCompatUILayout.inject(this);

        updateVisibilityOfViews();

        mViewHost.setView(mCompatUILayout, getWindowLayoutParams());
    }

    private void updateVisibilityOfViews() {
        // Size Compat mode restart button.
        mCompatUILayout.setRestartButtonVisibility(mHasSizeCompat);
        if (mHasSizeCompat && mShouldShowSizeCompatHint) {
            mCompatUILayout.setSizeCompatHintVisibility(/* show= */ true);
            // Only show by default for the first time.
            mShouldShowSizeCompatHint = false;
        }

        // Camera control for stretched issues.
        mCompatUILayout.setCameraControlVisibility(shouldShowCameraControl());
        if (shouldShowCameraControl() && mShouldShowCameraCompatHint) {
            mCompatUILayout.setCameraCompatHintVisibility(/* show= */ true);
            // Only show by default for the first time.
            mShouldShowCameraCompatHint = false;
        }
        if (shouldShowCameraControl()) {
            mCompatUILayout.updateCameraTreatmentButton(mCameraCompatControlState);
        }
    }

    private boolean shouldShowCameraControl() {
        return mCameraCompatControlState != CAMERA_COMPAT_CONTROL_HIDDEN
                && mCameraCompatControlState != CAMERA_COMPAT_CONTROL_DISMISSED;
    }

    @VisibleForTesting
    CompatUILayout inflateCompatUILayout() {
        return (CompatUILayout) LayoutInflater.from(mContext)
                .inflate(R.layout.compat_ui_layout, null);
    }

    @VisibleForTesting
    SurfaceControlViewHost createSurfaceViewHost() {
        return new SurfaceControlViewHost(mContext, mContext.getDisplay(), this);
    }

    /** Gets the layout params. */
    private WindowManager.LayoutParams getWindowLayoutParams() {
        // Measure how big the hint is since its size depends on the text size.
        mCompatUILayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        final WindowManager.LayoutParams winParams = new WindowManager.LayoutParams(
                // Cannot be wrap_content as this determines the actual window size
                mCompatUILayout.getMeasuredWidth(), mCompatUILayout.getMeasuredHeight(),
                TYPE_APPLICATION_OVERLAY,
                FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        winParams.token = new Binder();
        winParams.setTitle(CompatUILayout.class.getSimpleName() + mTaskId);
        winParams.privateFlags |= PRIVATE_FLAG_NO_MOVE_ANIMATION | PRIVATE_FLAG_TRUSTED_OVERLAY;
        return winParams;
    }

}
