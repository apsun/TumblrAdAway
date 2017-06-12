package com.crossbowffs.tumblradaway;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

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
        Object postId = XposedHelpers.callMethod(objectData, "getId");
        Enum<?> typeEnum = (Enum<?>)XposedHelpers.callMethod(objectData, "getTimelineObjectType");
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
        if ("POST".equals(typeStr)) return false;
        if ("BLOG_CARD".equals(typeStr)) return false;
        if ("BANNER".equals(typeStr)) return true;
        if ("CAROUSEL".equals(typeStr)) return true;
        if ("RICH_BANNER".equals(typeStr)) return true;
        if ("GEMINI_AD".equals(typeStr)) return true;
        if ("CLIENT_SIDE_MEDIATION".equals(typeStr)) return true;
        if ("FAN_MEDIATION".equals(typeStr)) return true;
        if ("TITLE".equals(typeStr)) return true;
        Xlog.w("Unknown post type: %s", typeStr);
        return false;
    }

    private static void forceEnableGraywater(XC_LoadPackage.LoadPackageParam lpparam) {
        Xutil.findAndHookMethod(
            "com.tumblr.App", lpparam.classLoader,
            "isBeta",
            XC_MethodReplacement.returnConstant(true));

        Xutil.findAndHookMethod(
            "com.tumblr.feature.Feature", lpparam.classLoader,
            "isEnabled", "com.tumblr.feature.Feature",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Enum<?> arg = (Enum<?>)param.args[0];
                    if ("GRAYWATER_DASHBOARD".equals(arg.name())) {
                        param.setResult(true);
                    }
                }
            });
    }

    private static void blockAdProvider(XC_LoadPackage.LoadPackageParam lpparam) {
        Xutil.findAndHookMethod(
            "com.tumblr.ad.AdProvider", lpparam.classLoader,
            "loadAds",
            Xutil.doNothingAndLog());
    }

    private static void blockSimpleTimelineAds(XC_LoadPackage.LoadPackageParam lpparam) {
        Xutil.findAndHookMethod(
            "com.tumblr.ui.widget.timelineadapter.SimpleTimelineAdapter", lpparam.classLoader,
            "applyItems", List.class, boolean.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    try {
                        removeAds((List<?>)param.args[0]);
                    } catch (Throwable e) {
                        Xlog.e("Error occurred while filtering ads", e);
                        throw e;
                    }
                }
            });
    }

    private static void blockGraywaterTimelineAds(XC_LoadPackage.LoadPackageParam lpparam) {
        Xutil.findAndHookMethod(
            "com.tumblr.graywater.GraywaterAdapter", lpparam.classLoader,
            "add", int.class, Object.class, boolean.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (isAd(param.args[1])) {
                            param.setResult(null);
                        }
                    } catch (Throwable e) {
                        Xlog.e("Error occurred while filtering ads (Graywater)", e);
                        throw e;
                    }
                }
            });
    }

    private static void blockExtendedFooter(XC_LoadPackage.LoadPackageParam lpparam) {
        Xutil.findAndHookMethod(
            "com.tumblr.model.PostAttribution", lpparam.classLoader,
            "shouldShowNewAppAttribution",
            XC_MethodReplacement.returnConstant(false));
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.tumblr".equals(lpparam.packageName)) {
            return;
        }

        Xlog.i("Tumblr AdAway initializing...");
        Xutil.printInitInfo(lpparam);

        // forceEnableGraywater(lpparam);
        blockAdProvider(lpparam);
        blockSimpleTimelineAds(lpparam);
        blockGraywaterTimelineAds(lpparam);
        blockExtendedFooter(lpparam);

        Xlog.i("Tumblr AdAway initialization complete!");
    }
}
