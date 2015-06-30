package com.crossbowffs.tumblradaway;

import android.database.Cursor;
import android.os.Build;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.File;
import java.util.List;

public class Hook implements IXposedHookLoadPackage {
    private static void removeAds(Object postAdapter) {
        Xlog.d("Dashboard refresh completed, filtering ads...");
        List<?> timeline = (List<?>)XposedHelpers.getObjectField(postAdapter, "mTimeline");
        int adCount = 0;
        int postCount = timeline.size();
        for (int i = postCount - 1; i >= 0; i--) {
            Object timelineObject = timeline.get(i);
            if (isAd(timelineObject)) {
                timeline.remove(i);
                adCount++;
            }
        }
        Xlog.d("%d/%d posts filtered", adCount, postCount);
    }

    private static boolean isAd(Object timelineObject) {
        Enum<?> typeEnum = (Enum<?>)XposedHelpers.getObjectField(timelineObject, "mType");
        String typeStr = typeEnum.name();
        if ("BANNER".equals(typeStr)) {
            String bannerId = (String)XposedHelpers.getObjectField(timelineObject, "mBannerId");
            Xlog.d("Blocked post: mType == BANNER <BannerID %s>", bannerId);
        } else if ("CAROUSEL".equals(typeStr)) {
            String carouselId = (String)XposedHelpers.getObjectField(timelineObject, "mCarouselId");
            Xlog.d("Blocked post: mType == CAROUSEL <CarouselID %s>", carouselId);
        } else if (!"POST".equals(typeStr)) {
            Xlog.w("Unknown post type: %s", typeStr);
        }

        boolean isSponsored = (Boolean)XposedHelpers.callMethod(timelineObject, "isSponsored");
        if (isSponsored) {
            long tumblrId = XposedHelpers.getLongField(timelineObject, "mTumblrId");
            Xlog.d("Blocked post: isSponsored() == true <TumblrID %d>", tumblrId);
            return true;
        }

        return false;
    }

    private static String getPackageVersion(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> parserCls = XposedHelpers.findClass("android.content.pm.PackageParser", lpparam.classLoader);
        Object parser;
        try {
            parser = parserCls.newInstance();
        } catch (InstantiationException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        }
        File apkPath = new File(lpparam.appInfo.sourceDir);
        Object pkg = XposedHelpers.callMethod(parser, "parsePackage", apkPath, 0);
        return (String)XposedHelpers.getObjectField(pkg, "mVersionName");
    }

    private static void printInitInfo(XC_LoadPackage.LoadPackageParam lpparam) {
        Xlog.i("Tumblr AdAway initializing...");
        Xlog.i("Phone manufacturer: %s", Build.MANUFACTURER);
        Xlog.i("Phone model: %s", Build.MODEL);
        Xlog.i("Android version: %s", Build.VERSION.RELEASE);
        Xlog.i("Xposed bridge version: %d", XposedBridge.XPOSED_BRIDGE_VERSION);
        Xlog.i("Tumblr APK version: %s", getPackageVersion(lpparam));
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.tumblr".equals(lpparam.packageName)) {
            return;
        }

        printInitInfo(lpparam);

        XposedHelpers.findAndHookMethod(
            "com.tumblr.ui.widget.postadapter.PostAdapter", lpparam.classLoader,
            "save", Cursor.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        removeAds(param.thisObject);
                    } catch (Throwable e) {
                        Xlog.e("Error occurred while filtering ads", e);
                    }
                }
            }
        );
    }
}
