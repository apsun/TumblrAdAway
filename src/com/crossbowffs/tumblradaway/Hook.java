package com.crossbowffs.tumblradaway;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.List;

public class Hook implements IXposedHookLoadPackage {
    private static String getTimelineObjectType(Object timelineObject) {
        Object objectData = XposedHelpers.callMethod(timelineObject, "getObjectData");
        Enum<?> typeEnum = (Enum<?>)XposedHelpers.callMethod(objectData, "getTimelineObjectType");
        return typeEnum.name();
    }

    private static boolean isAdType(String typeStr) {
        // This list should be ordered in decreasing frequency
        // of appearance for maximum performance!
        if ("POST".equals(typeStr)) return false;
        if ("BLOG_CARD".equals(typeStr)) return false;
        if ("TITLE".equals(typeStr)) return false;
        if ("CHICLET_ROW".equals(typeStr)) return false;
        if ("TRENDING_TOPIC".equals(typeStr)) return false;
        if ("FOLLOWED_SEARCH_TAG_RIBBON".equals(typeStr)) return false;
        if ("BANNER".equals(typeStr)) return true;
        if ("CAROUSEL".equals(typeStr)) return true;
        if ("RICH_BANNER".equals(typeStr)) return true;
        if ("GEMINI_AD".equals(typeStr)) return true;
        if ("CLIENT_SIDE_MEDIATION".equals(typeStr)) return true;
        if ("FAN_MEDIATION".equals(typeStr)) return true;
        if ("TAG_RIBBON".equals(typeStr)) return true;
        Xlog.w("Unknown post type: %s", typeStr);
        return false;
    }

    private static boolean isSponsored(Object timelineObject) {
        return (Boolean)XposedHelpers.callMethod(timelineObject, "isSponsored");
    }

    private static boolean isTitleType(Object typeStr) {
        return "TITLE".equals(typeStr);
    }

    private static boolean isAd(Object timelineObject, boolean removedNext) {
        String objType = getTimelineObjectType(timelineObject);

        if (isAdType(objType)) {
            Xlog.i("Blocked object: getTimelineObjectType() == %s", objType);
            return true;
        }

        if (isSponsored(timelineObject)) {
            Xlog.i("Blocked object: isSponsored() == true");
            return true;
        }

        // Removes any titles that immediately precede an ad
        // object. Note that not all titles are evil, so we
        // can't just block them all.
        if (removedNext && isTitleType(objType)) {
            Xlog.i("Blocked object: title preceding ad");
            return true;
        }

        return false;
    }

    private static void removeAds(List<?> timeline) {
        Xlog.i("Dashboard refresh completed, filtering ads...");
        int adCount = 0;
        int postCount = timeline.size();
        boolean removedNext = false;
        for (int i = postCount - 1; i >= 0; i--) {
            Object timelineObject = timeline.get(i);
            if (isAd(timelineObject, removedNext)) {
                timeline.remove(i);
                adCount++;
                removedNext = true;
            } else {
                removedNext = false;
            }
        }
        Xlog.i("%d/%d posts filtered", adCount, postCount);
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

    private static void blockGraywaterTimelineAdsMulti(XC_LoadPackage.LoadPackageParam lpparam) {
        Xutil.findAndHookMethod(
            "com.tumblr.ui.widget.graywater.GraywaterTimelineAdapter", lpparam.classLoader,
            "applyItems", List.class, boolean.class, boolean.class,
            new XC_MethodHook() {
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
    }

    private static void blockGraywaterTimelineAdsSingle(XC_LoadPackage.LoadPackageParam lpparam) {
        // This one is for the ones that somehow fall through the
        // cracks after filtering the timeline. Not sure if this
        // is still necessary, but better safe than sorry, I guess.
        Xutil.findAndHookMethod(
            "com.tumblr.graywater.GraywaterAdapter", lpparam.classLoader,
            "add", int.class, Object.class, boolean.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (isAd(param.args[1], false)) {
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

        blockAdProvider(lpparam);
        blockSimpleTimelineAds(lpparam);
        blockGraywaterTimelineAdsMulti(lpparam);
        blockGraywaterTimelineAdsSingle(lpparam);
        blockExtendedFooter(lpparam);

        Xlog.i("Tumblr AdAway initialization complete!");
    }
}
