package com.crossbowffs.tumblradaway;

import android.os.Build;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.File;
import java.util.List;

public class Hook implements IXposedHookLoadPackage {
    private static void removeAds(List<?> timeline) {
        Xlog.d("Dashboard refresh completed, filtering ads...");
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
            return true;
        } else if ("CAROUSEL".equals(typeStr)) {
            String carouselId = (String)XposedHelpers.getObjectField(timelineObject, "mCarouselId");
            Xlog.d("Blocked post: mType == CAROUSEL <CarouselID %s>", carouselId);
            return true;
        } else if ("RICH_BANNER".equals(typeStr)) {
            String richBannerId = (String)XposedHelpers.getObjectField(timelineObject, "mRichBannerId");
            Xlog.d("Blocked post: mType == RICH_BANNER <RichBannerID %s>", richBannerId);
            return true;
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
        String versionName = (String)XposedHelpers.getObjectField(pkg, "mVersionName");
        int versionCode = XposedHelpers.getIntField(pkg, "mVersionCode");
        return String.format("%s (%d)", versionName, versionCode);
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
            XposedHelpers.findAndHookMethod(
                "com.tumblr.ui.widget.timelineadapter.TimelineAdapter", lpparam.classLoader,
                "applyItems", "com.tumblr.timeline.TimelineProvider$RequestType", List.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            removeAds((List<?>)param.args[1]);
                        } catch (Throwable e) {
                            Xlog.e("Error occurred while filtering ads", e);
                        }
                    }
                }
            );

            XposedHelpers.findAndHookMethod(
                "com.tumblr.model.PostAttribution", lpparam.classLoader,
                "shouldShowNewAppAttribution", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(false);
                    }
                }
            );
        } catch (Throwable e) {
            Xlog.e("Exception occurred while hooking methods", e);
            throw e;
        }
    }
}
