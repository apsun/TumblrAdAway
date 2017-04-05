package com.crossbowffs.tumblradaway;

import android.os.Build;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.File;
import java.util.List;

public class Hook implements IXposedHookLoadPackage {
    private static void removeAds(List<?> timeline) {
        Xlog.i("Dashboard refresh completed, filtering ads...");
        int adCount = 0;
        int postCount = timeline.size();
        for (int i = postCount - 1; i >= 0; i--) {
            Object timelineObject = timeline.get(i);
            if (isAd(timelineObject)) {
                timeline.remove(i);
                adCount++;
            }
        }
        Xlog.i("%d/%d posts filtered", adCount, postCount);
    }

    private static boolean isAd(Object timelineObject) {
        Object objectData = XposedHelpers.callMethod(timelineObject, "getObjectData");
        Enum<?> typeEnum = (Enum<?>)XposedHelpers.callMethod(objectData, "getTimelineObjectType");
        Object postId = XposedHelpers.callMethod(objectData, "getId");
        String typeStr = typeEnum.name();

        if (isAdType(typeStr)) {
            Xlog.i("Blocked post: getTimelineObjectType() == %s <ID %s>", typeStr, postId);
            return true;
        }

        boolean isSponsored = (Boolean)XposedHelpers.callMethod(timelineObject, "isSponsored");
        if (isSponsored) {
            Xlog.i("Blocked post: isSponsored() == true <ID %s>", postId);
            return true;
        }

        return false;
    }

    private static boolean isAdType(String typeStr) {
        if ("BANNER".equals(typeStr)) return true;
        if ("CAROUSEL".equals(typeStr)) return true;
        if ("RICH_BANNER".equals(typeStr)) return true;
        if ("GEMINI_AD".equals(typeStr)) return true;
        if ("BLOG_CARD".equals(typeStr)) return false;
        if ("POST".equals(typeStr)) return false;
        Xlog.w("Unknown post type: %s", typeStr);
        return false;
    }

    private static String getPackageVersion(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> parserCls = XposedHelpers.findClass("android.content.pm.PackageParser", lpparam.classLoader);
            Object parser = parserCls.newInstance();
            File apkPath = new File(lpparam.appInfo.sourceDir);
            Object pkg = XposedHelpers.callMethod(parser, "parsePackage", apkPath, 0);
            String versionName = (String)XposedHelpers.getObjectField(pkg, "mVersionName");
            int versionCode = XposedHelpers.getIntField(pkg, "mVersionCode");
            return String.format("%s (%d)", versionName, versionCode);
        } catch (Throwable e) {
            return null;
        }
    }

    private static void printInitInfo(XC_LoadPackage.LoadPackageParam lpparam) {
        Xlog.i("Tumblr AdAway initializing...");
        Xlog.i("Phone manufacturer: %s", Build.MANUFACTURER);
        Xlog.i("Phone model: %s", Build.MODEL);
        Xlog.i("Android version: %s", Build.VERSION.RELEASE);
        Xlog.i("Xposed bridge version: %d", XposedBridge.XPOSED_BRIDGE_VERSION);
        Xlog.i("Tumblr APK version: %s", getPackageVersion(lpparam));
        Xlog.i("Tumblr AdAway version: %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.tumblr".equals(lpparam.packageName)) {
            return;
        }

        printInitInfo(lpparam);

        try {
            // Remove timeline ads
            XposedHelpers.findAndHookMethod(
                "com.tumblr.ui.widget.timelineadapter.SimpleTimelineAdapter", lpparam.classLoader,
                "applyItems", List.class, boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            removeAds((List<?>)param.args[0]);
                        } catch (Throwable e) {
                            Xlog.e("Error occurred while filtering ads", e);
                            throw e;
                        }
                    }
                });

            // Remove extended app attribution footer
            XposedHelpers.findAndHookMethod(
                "com.tumblr.model.PostAttribution", lpparam.classLoader,
                "shouldShowNewAppAttribution", XC_MethodReplacement.returnConstant(false));

        } catch (Throwable e) {
            Xlog.e("Exception occurred while hooking methods", e);
            throw e;
        }
    }
}
