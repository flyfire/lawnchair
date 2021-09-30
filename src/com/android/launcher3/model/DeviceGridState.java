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

package com.android.launcher3.model;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_2;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_3;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_4;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_5;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.IntDef;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.StatsLogManager.LauncherEvent;
import com.android.launcher3.util.IntSet;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility class representing persisted grid properties.
 */
public class DeviceGridState {

    public static final String KEY_WORKSPACE_SIZE = "migration_src_workspace_size";
    public static final String KEY_HOTSEAT_COUNT = "migration_src_hotseat_count";
    public static final String KEY_DEVICE_TYPE = "migration_src_device_type";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_PHONE, TYPE_MULTI_DISPLAY, TYPE_TABLET})
    public @interface DeviceType{}
    public static final int TYPE_PHONE = 0;
    public static final int TYPE_MULTI_DISPLAY = 1;
    public static final int TYPE_TABLET = 2;

    private static final IntSet COMPATIBLE_TYPES = IntSet.wrap(TYPE_PHONE, TYPE_MULTI_DISPLAY);

    public static boolean deviceTypeCompatible(@DeviceType int typeA, @DeviceType int typeB) {
        return typeA == typeB
                || (COMPATIBLE_TYPES.contains(typeA) && COMPATIBLE_TYPES.contains(typeB));
    }

    private final String mGridSizeString;
    private final int mNumHotseat;
    private final @DeviceType int mDeviceType;

    public DeviceGridState(InvariantDeviceProfile idp) {
        mGridSizeString = String.format(Locale.ENGLISH, "%d,%d", idp.numColumns, idp.numRows);
        mNumHotseat = idp.numDatabaseHotseatIcons;
        mDeviceType = idp.supportedProfiles.size() > 2
                ? TYPE_MULTI_DISPLAY
                : idp.supportedProfiles.stream().allMatch(dp -> dp.isTablet)
                        ? TYPE_TABLET
                        : TYPE_PHONE;
    }

    public DeviceGridState(Context context) {
        SharedPreferences prefs = Utilities.getPrefs(context);
        mGridSizeString = prefs.getString(KEY_WORKSPACE_SIZE, "");
        mNumHotseat = prefs.getInt(KEY_HOTSEAT_COUNT, -1);
        mDeviceType = prefs.getInt(KEY_DEVICE_TYPE, TYPE_PHONE);
    }

    /**
     * Returns the device type for the grid
     */
    public @DeviceType int getDeviceType() {
        return mDeviceType;
    }

    /**
     * Stores the device state to shared preferences
     */
    public void writeToPrefs(Context context) {
        Utilities.getPrefs(context).edit()
                .putString(KEY_WORKSPACE_SIZE, mGridSizeString)
                .putInt(KEY_HOTSEAT_COUNT, mNumHotseat)
                .putInt(KEY_DEVICE_TYPE, mDeviceType)
                .apply();
    }

    /**
     * Returns the logging event corresponding to the grid state
     */
    public LauncherEvent getWorkspaceSizeEvent() {
        if (!TextUtils.isEmpty(mGridSizeString)) {
            switch (mGridSizeString.charAt(0)) {
                case '5':
                    return LAUNCHER_GRID_SIZE_5;
                case '4':
                    return LAUNCHER_GRID_SIZE_4;
                case '3':
                    return LAUNCHER_GRID_SIZE_3;
                case '2':
                    return LAUNCHER_GRID_SIZE_2;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "DeviceGridState{"
                + "mGridSizeString='" + mGridSizeString + '\''
                + ", mNumHotseat=" + mNumHotseat
                + ", mDeviceType=" + mDeviceType
                + '}';
    }

    /**
     * Returns true if the database from another DeviceGridState can be loaded into the current
     * DeviceGridState without migration, or false otherwise.
     */
    public boolean isCompatible(DeviceGridState other) {
        if (this == other) return true;
        if (other == null) return false;
        boolean isCompatible = mNumHotseat == other.mNumHotseat
                && deviceTypeCompatible(mDeviceType, other.mDeviceType)
                && Objects.equals(mGridSizeString, other.mGridSizeString);
        // TODO(b/198965093): Temporary fix for multi-display devices, ignore hotseat size changes
        //  and type compatibility.
        if ((mDeviceType == TYPE_MULTI_DISPLAY || other.mDeviceType == TYPE_MULTI_DISPLAY)
                && !isCompatible && Objects.equals(mGridSizeString, other.mGridSizeString)) {
            Log.d("b/198965093", "Hotseat and deice type compatibility ignored: " + this
                    + ", other: " + other);
            isCompatible = true;
        }
        return isCompatible;
    }
}