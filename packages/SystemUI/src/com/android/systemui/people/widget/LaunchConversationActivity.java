/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.people.widget;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
import android.util.Log;

import com.android.systemui.people.PeopleSpaceUtils;

/** Proxy activity to launch ShortcutInfo's conversation. */
public class LaunchConversationActivity extends Activity {
    private static final String TAG = "PeopleSpaceLaunchConv";
    private static final boolean DEBUG = PeopleSpaceUtils.DEBUG;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate called");

        Intent intent = getIntent();
        ShortcutInfo shortcutInfo = (ShortcutInfo) intent.getParcelableExtra(
                PeopleSpaceWidgetProvider.EXTRA_SHORTCUT_INFO
        );
        if (shortcutInfo != null) {
            if (DEBUG) {
                Log.d(TAG, "Launching conversation with shortcutInfo id " + shortcutInfo.getId());
            }
            try {
                LauncherApps launcherApps =
                        getApplicationContext().getSystemService(LauncherApps.class);
                launcherApps.startShortcut(
                        shortcutInfo, null, null);
            } catch (Exception e) {
                Log.e(TAG, "Exception starting shortcut:" + e);
            }
        } else {
            if (DEBUG) Log.d(TAG, "Trying to launch conversation with null shortcutInfo.");
        }
        finish();
    }
}
