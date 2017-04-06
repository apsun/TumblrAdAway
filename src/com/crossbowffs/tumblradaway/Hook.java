package com.crossbowffs.tumblradaway;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.List;

public class Hook implements IXposedHookLoadPackage {
    private static class RemoveAdsHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            try {
                Xlog.i("Called %s", Xutil.getMethodName(param));
                removeAds((List<?>)param.args[0]);
            } catch (Throwable e) {
                Xlog.e("Error occurred while filtering ads", e);
                throw e;
            }
        }
    }

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
        if ("POST".equals(typeStr)) return false;
        if ("BLOG_CARD".equals(typeStr)) return false;
        if ("BANNER".equals(typeStr)) return true;
        if ("CAROUSEL".equals(typeStr)) return true;
        if ("RICH_BANNER".equals(typeStr)) return true;
        if ("GEMINI_AD".equals(typeStr)) return true;
        if ("CLIENT_SIDE_MEDIATION".equals(typeStr)) return true;
        if ("FAN_MEDIATION".equals(typeStr)) return true;
        Xlog.w("Unknown post type: %s", typeStr);
        return false;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.tumblr".equals(lpparam.packageName)) {
            return;
        }

        Xlog.i("Tumblr AdAway initializing...");
        Xutil.printInitInfo(lpparam);

        // Block ALL the ads!
        Xutil.findAndHookMethod(
            "com.tumblr.ad.AdProvider", lpparam.classLoader,
            "loadAds",
            Xutil.doNothingAndLog());

        // Remove timeline ads
        Xutil.findAndHookMethod(
            "com.tumblr.ui.widget.timelineadapter.SimpleTimelineAdapter", lpparam.classLoader,
            "applyItems", List.class, boolean.class,
            new RemoveAdsHook());

        // Remove timeline ads (new version)
        Xutil.findAndHookMethod(
            "com.tumblr.ui.widget.graywater.GraywaterTimelineAdapter", lpparam.classLoader,
            "applyItems", List.class, boolean.class,
            new RemoveAdsHook());

        // Remove extended app attribution footer
        Xutil.findAndHookMethod(
            "com.tumblr.model.PostAttribution", lpparam.classLoader,
            "shouldShowNewAppAttribution",
            XC_MethodReplacement.returnConstant(false));

        Xlog.i("Tumblr AdAway initialization complete!");
    }
}
