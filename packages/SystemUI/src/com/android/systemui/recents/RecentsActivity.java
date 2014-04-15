/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.recents;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import com.android.systemui.R;
import com.android.systemui.recents.model.SpaceNode;
import com.android.systemui.recents.model.TaskStack;
import com.android.systemui.recents.views.RecentsView;

import java.util.ArrayList;


/* Activity */
public class RecentsActivity extends Activity implements RecentsView.RecentsViewCallbacks {
    FrameLayout mContainerView;
    RecentsView mRecentsView;
    View mEmptyView;

    boolean mVisible;
    boolean mTaskLaunched;

    // Broadcast receiver to handle messages from our RecentsService
    BroadcastReceiver mServiceBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Console.log(Constants.DebugFlags.App.SystemUIHandshake,
                    "[RecentsActivity|serviceBroadcast]", action, Console.AnsiRed);
            if (action.equals(RecentsService.ACTION_TOGGLE_RECENTS_ACTIVITY)) {
                // Try and unfilter and filtered stacks
                if (!mRecentsView.unfilterFilteredStacks()) {
                    // If there are no filtered stacks, dismiss recents and launch the first task
                    dismissRecentsIfVisible();
                }
            }
        }
    };

    // Broadcast receiver to handle messages from the system
    BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    /** Updates the set of recent tasks */
    void updateRecentsTasks(Intent launchIntent) {
        // Update the configuration based on the launch intent
        RecentsConfiguration config = RecentsConfiguration.getInstance();
        config.launchedWithThumbnailAnimation = launchIntent.getBooleanExtra(
                AlternateRecentsComponent.EXTRA_ANIMATING_WITH_THUMBNAIL, false);

        RecentsTaskLoader loader = RecentsTaskLoader.getInstance();
        SpaceNode root = loader.reload(this, Constants.Values.RecentsTaskLoader.PreloadFirstTasksCount);
        ArrayList<TaskStack> stacks = root.getStacks();
        if (!stacks.isEmpty()) {
            mRecentsView.setBSP(root);
        }

        // Add the default no-recents layout
        if (stacks.size() == 1 && stacks.get(0).getTaskCount() == 0) {
            mEmptyView.setVisibility(View.VISIBLE);

            // Dim the background even more
            WindowManager.LayoutParams wlp = getWindow().getAttributes();
            wlp.dimAmount = Constants.Values.Window.DarkBackgroundDim;
            getWindow().setAttributes(wlp);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        } else {
            mEmptyView.setVisibility(View.GONE);

            // Un-dim the background
            WindowManager.LayoutParams wlp = getWindow().getAttributes();
            wlp.dimAmount = 0f;
            getWindow().setAttributes(wlp);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    /** Dismisses recents if we are already visible and the intent is to toggle the recents view */
    boolean dismissRecentsIfVisible() {
        if (mVisible) {
            if (!mRecentsView.launchFirstTask()) {
                finish();
            }
            return true;
        }
        return false;
    }

    /** Called with the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Console.logDivider(Constants.DebugFlags.App.SystemUIHandshake);
        Console.log(Constants.DebugFlags.App.SystemUIHandshake, "[RecentsActivity|onCreate]",
                getIntent().getAction() + " visible: " + mVisible, Console.AnsiRed);
        Console.logTraceTime(Constants.DebugFlags.App.TimeRecentsStartup,
                Constants.DebugFlags.App.TimeRecentsStartupKey, "onCreate");

        // Initialize the loader and the configuration
        RecentsTaskLoader.initialize(this);
        RecentsConfiguration.reinitialize(this);

        // Create the view hierarchy
        mRecentsView = new RecentsView(this);
        mRecentsView.setCallbacks(this);
        mRecentsView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Create the empty view
        LayoutInflater inflater = LayoutInflater.from(this);
        mEmptyView = inflater.inflate(R.layout.recents_empty, mContainerView, false);

        mContainerView = new FrameLayout(this);
        mContainerView.addView(mRecentsView);
        mContainerView.addView(mEmptyView);
        setContentView(mContainerView);

        // Update the recent tasks
        updateRecentsTasks(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Reset the task launched flag if we encounter an onNewIntent() before onStop()
        mTaskLaunched = false;

        Console.logDivider(Constants.DebugFlags.App.SystemUIHandshake);
        Console.log(Constants.DebugFlags.App.SystemUIHandshake, "[RecentsActivity|onNewIntent]",
                intent.getAction() + " visible: " + mVisible, Console.AnsiRed);
        Console.logTraceTime(Constants.DebugFlags.App.TimeRecentsStartup,
                Constants.DebugFlags.App.TimeRecentsStartupKey, "onNewIntent");

        // Initialize the loader and the configuration
        RecentsTaskLoader.initialize(this);
        RecentsConfiguration.reinitialize(this);

        // Update the recent tasks
        updateRecentsTasks(intent);
    }

    @Override
    protected void onStart() {
        Console.log(Constants.DebugFlags.App.SystemUIHandshake, "[RecentsActivity|onStart]", "",
                Console.AnsiRed);
        super.onStart();
        mVisible = true;
    }

    @Override
    protected void onResume() {
        Console.log(Constants.DebugFlags.App.SystemUIHandshake, "[RecentsActivity|onResume]", "",
                Console.AnsiRed);
        super.onResume();
    }

    @Override
    public void onAttachedToWindow() {
        Console.log(Constants.DebugFlags.App.SystemUIHandshake,
                "[RecentsActivity|onAttachedToWindow]", "",
                Console.AnsiRed);
        super.onAttachedToWindow();

        // Register the broadcast receiver to handle messages from our service
        IntentFilter filter = new IntentFilter();
        filter.addAction(RecentsService.ACTION_TOGGLE_RECENTS_ACTIVITY);
        registerReceiver(mServiceBroadcastReceiver, filter);

        // Register the broadcast receiver to handle messages when the screen is turned off
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenOffReceiver, filter);
    }

    @Override
    public void onDetachedFromWindow() {
        Console.log(Constants.DebugFlags.App.SystemUIHandshake,
                "[RecentsActivity|onDetachedFromWindow]", "",
                Console.AnsiRed);
        super.onDetachedFromWindow();

        // Unregister any broadcast receivers we have registered
        unregisterReceiver(mServiceBroadcastReceiver);
        unregisterReceiver(mScreenOffReceiver);
    }

    @Override
    protected void onPause() {
        Console.log(Constants.DebugFlags.App.SystemUIHandshake, "[RecentsActivity|onPause]", "",
                Console.AnsiRed);
        super.onPause();
    }

    @Override
    protected void onStop() {
        Console.log(Constants.DebugFlags.App.SystemUIHandshake, "[RecentsActivity|onStop]", "",
                Console.AnsiRed);
        super.onStop();

        mVisible = false;
        mTaskLaunched = false;
    }

    @Override
    protected void onDestroy() {
        Console.log(Constants.DebugFlags.App.SystemUIHandshake, "[RecentsActivity|onDestroy]", "",
                Console.AnsiRed);
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        RecentsTaskLoader loader = RecentsTaskLoader.getInstance();
        if (loader != null) {
            loader.onTrimMemory(level);
        }
    }

    @Override
    public void onBackPressed() {
        boolean interceptedByInfoPanelClose = false;

        // Try and return from any open info panes
        if (Constants.DebugFlags.App.EnableInfoPane) {
            interceptedByInfoPanelClose = mRecentsView.closeOpenInfoPanes();
        }

        // If we haven't been intercepted already, then unfilter any stacks
        if (!interceptedByInfoPanelClose) {
            if (!mRecentsView.unfilterFilteredStacks()) {
                super.onBackPressed();
            }
        }
    }

    @Override
    public void onTaskLaunching() {
        mTaskLaunched = true;
    }
}
