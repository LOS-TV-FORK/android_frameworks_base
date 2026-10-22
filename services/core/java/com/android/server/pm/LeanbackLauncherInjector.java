/*
 * Copyright (C) 2026 The LineageOS Project
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

package com.android.server.pm;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.pm.parsing.pkg.AndroidPackageLegacyUtils;
import com.android.internal.pm.pkg.component.ParsedActivity;
import com.android.internal.pm.pkg.component.ParsedActivityImpl;
import com.android.internal.pm.pkg.component.ParsedIntentInfo;
import com.android.internal.pm.pkg.component.ParsedIntentInfoImpl;
import com.android.server.pm.pkg.AndroidPackage;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes apps that were written for a phone or a tablet show up in TV launchers.
 *
 * <p>A TV launcher only lists activities that answer to
 * {@link Intent#CATEGORY_LEANBACK_LAUNCHER}. An app that never declared that category stays
 * invisible on a TV whatever the display hardware does, because the decision is taken from the
 * manifest. There is nothing the display stack can do about that at runtime.
 *
 * <p>The category is added while the manifest is being parsed, inside system_server, so the
 * application itself is never touched: no resource is added, no file is rewritten and no
 * signature has to change.
 *
 * <p>Only apps that already expose a launcher activity, i.e. the apps a phone or tablet shows
 * an icon for, are considered, and only when the package does not provide a Leanback activity of
 * its own. System apps are left alone: the permission controller and the various system dialogs
 * are launched by other parts of the system, and putting them on the TV home screen would only
 * be noise.
 *
 * <p>The generated category is remembered so that the developer setting can hide those entries
 * again without a reboot; see {@link #isInjectionEnabled()}.
 */
public final class LeanbackLauncherInjector {

    /** Toggled from the developer options. */
    public static final String PROPERTY = "persist.sys.los.leanback_inject";

    private static final String TAG = "LeanbackLauncherInjector";

    /** Component names of the activities that were given the category by us. */
    private static final Set<String> sInjected = ConcurrentHashMap.newKeySet();

    private LeanbackLauncherInjector() {}

    /**
     * Adds a Leanback launcher category to the launcher activity of a package that has none.
     *
     * @param pkg the freshly parsed package, still mutable
     */
    public static void inject(@android.annotation.Nullable AndroidPackage pkg) {
        if (pkg == null || !isInjectionEnabled()) {
            return;
        }
        // Preinstalled apps are part of the system image and were given the categories their
        // authors saw fit. Only apps that came in afterwards are candidates.
        if (AndroidPackageLegacyUtils.isSystem(pkg)) {
            return;
        }
        final List<ParsedActivity> activities = pkg.getActivities();
        if (activities == null || activities.isEmpty() || hasLeanbackActivity(activities)) {
            return;
        }
        for (ParsedActivity activity : activities) {
            // Only the parser's own implementation can take a new intent; anything else is
            // left exactly as it was.
            if (activity instanceof ParsedActivityImpl
                    && isLauncherActivity(activity) && canBeShownOnTv(activity)) {
                addLeanbackCategory((ParsedActivityImpl) activity, pkg.getPackageName());
            }
        }
    }

    /**
     * Whether the injected categories may be listed by launchers right now. Turning this off
     * hides the entries again immediately, without a reboot and without touching the parsed
     * packages, which keeps the escape hatch usable when the injection misbehaves.
     */
    public static boolean isInjectionEnabled() {
        return !"0".equals(SystemProperties.get(PROPERTY, "1"));
    }

    /** Whether the category of the given activity was added by us rather than by its author. */
    public static boolean isInjected(@android.annotation.Nullable String packageName,
            @android.annotation.Nullable String className) {
        return packageName != null && className != null
                && sInjected.contains(packageName + "/" + className);
    }

    private static boolean hasLeanbackActivity(List<ParsedActivity> activities) {
        for (ParsedActivity activity : activities) {
            for (ParsedIntentInfo intent : activity.getIntents()) {
                if (intent.getIntentFilter().hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isLauncherActivity(ParsedActivity activity) {
        for (ParsedIntentInfo intent : activity.getIntents()) {
            final IntentFilter filter = intent.getIntentFilter();
            if (filter.hasAction(Intent.ACTION_MAIN)
                    && filter.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canBeShownOnTv(ParsedActivity activity) {
        // A launcher can only start an exported activity.
        if (!activity.isExported()) {
            return false;
        }
        // A portrait-only app is unusable on a television, so it is not worth an entry.
        switch (activity.getScreenOrientation()) {
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                return false;
            default:
                return true;
        }
    }

    private static void addLeanbackCategory(ParsedActivityImpl activity, String packageName) {
        final ParsedIntentInfoImpl leanback = new ParsedIntentInfoImpl();
        final IntentFilter filter = leanback.getIntentFilter();
        filter.addAction(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        activity.addIntent(leanback);

        final String className = activity.getComponentName() != null
                ? activity.getComponentName().getClassName() : activity.getName();
        sInjected.add(packageName + "/" + className);
        Log.i(TAG, "Added a Leanback category to " + packageName + "/" + className);
    }
}