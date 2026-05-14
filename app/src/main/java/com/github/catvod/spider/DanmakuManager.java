package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.spider.entity.DanmakuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DanmakuManager {

    public static String lastAutoDanmakuUrl = "";  // 上次自动推送的弹幕URL
    public static String lastManualDanmakuUrl = ""; // 上次手动选择的弹幕URL
    public static String lastDanmakuUrl = ""; // 上次弹幕URL
    public static ConcurrentMap<Integer, DanmakuItem> lastDanmakuItemMap = new ConcurrentHashMap<>();
    public static ConcurrentMap<String, DanmakuItem> lastDanmakuUrlItemMap = new ConcurrentHashMap<>();
    public static ConcurrentMap<String, DanmakuItem> seriesDanmakuCache = new ConcurrentHashMap<>();
    public static int lastDanmakuId = -1;          // 上次的弹幕ID
    public static boolean hasAutoSearched = false; // 是否已自动搜索过
    public static String lastProcessedTitle = "";  // 上次处理的标题
    public static String currentVideoSignature = "";  // 当前视频的唯一标识（基于标题提取）
    public static long lastVideoDetectedTime = 0;     // 上次检测到视频的时间

    public static void recordDanmakuUrl(DanmakuItem danmakuItem, boolean isAuto) {
        if (danmakuItem == null) return;
        if (isAuto) {
            lastAutoDanmakuUrl = danmakuItem.getDanmakuUrl();
            DanmakuSpider.log("记录自动弹幕URL: " + danmakuItem.getDanmakuUrl());
        } else {
            lastManualDanmakuUrl = danmakuItem.getDanmakuUrl();
            DanmakuSpider.log("记录手动弹幕URL: " + danmakuItem.getDanmakuUrl());
        }
        lastDanmakuUrl = danmakuItem.getDanmakuUrl();
        lastDanmakuId = danmakuItem.getEpId() != null ? danmakuItem.getEpId() : -1;
        if (danmakuItem.getEpId() != null) {
            lastDanmakuItemMap.put(danmakuItem.getEpId(), danmakuItem);
        }
        if (danmakuItem.getDanmakuUrl() != null) {
            lastDanmakuUrlItemMap.put(danmakuItem.getDanmakuUrl(), danmakuItem);
        }
        cacheDanmakuItem(danmakuItem);

        // 记录视频检测时间
        lastVideoDetectedTime = System.currentTimeMillis();
//        DanmakuSpider.log("✅ 更新视频检测时间: " + lastVideoDetectedTime);

        // 设置已搜索过，这样换集时就会尝试递增
        if (lastDanmakuId > 0) {
            hasAutoSearched = true;
//            DanmakuSpider.log("✅ 设置 hasAutoSearched = true (ID: " + lastDanmakuId + ")");
        }
    }

    public static DanmakuItem getNextDanmakuItem(int currentEpisodeNum, int newEpisodeNum) {
        int baseId = lastDanmakuId;
        int nextId = baseId + (newEpisodeNum - currentEpisodeNum);
        DanmakuSpider.log("📝 获取下一个弹幕URL: " + baseId + " -> " + nextId);

        if (nextId <= 0) {
            return null;
        }

        DanmakuItem lastItem = getLastDanmakuItem();
        DanmakuItem nextDanmakuItem = findContinuationItemById(nextId, lastItem);
        if (nextDanmakuItem != null) {
            DanmakuSpider.log("✅ 获取到下一个弹幕弹幕信息: " + nextDanmakuItem.toString());
            return nextDanmakuItem;
        }

        DanmakuSpider.log("🧮 ID位移缓存未命中: " + baseId + " -> " + nextId);
        return null;
    }

    public static void cacheDanmakuItems(List<DanmakuItem> items) {
        if (items == null) return;
        for (DanmakuItem item : items) {
            cacheDanmakuItem(item);
        }
    }

    public static void cacheDanmakuItem(DanmakuItem item) {
        if (item == null || item.getEpId() == null || TextUtils.isEmpty(item.getApiBase())) return;
        String titleKey = normalizeSeriesKey(getItemSeriesName(item));
        if (TextUtils.isEmpty(titleKey)) return;
        String sourceKey = normalizeSourceKey(item.getFrom());
        seriesDanmakuCache.put(titleKey + "#" + sourceKey + "#" + item.getApiBase() + "#" + item.getEpId(), item);
    }

    public static List<DanmakuItem> getSeriesCacheSnapshot() {
        return new ArrayList<>(seriesDanmakuCache.values());
    }

    public static String getItemSeriesName(DanmakuItem item) {
        if (item == null) return "";
        if (!TextUtils.isEmpty(item.getAnimeTitle())) return item.getAnimeTitle();
        return item.getTitle();
    }

    public static String normalizeSeriesKey(String title) {
        if (TextUtils.isEmpty(title)) return "";
        return title
                .replaceAll("(?i)\\s*from\\s+.*$", "")
                .replaceAll("\\s*\\(\\d{4}\\)\\s*", "")
                .replaceAll("[\\s\\p{Punct}，。；：、【】《》“”‘’（）]+", "")
                .toLowerCase();
    }

    public static String normalizeSourceKey(String source) {
        if (TextUtils.isEmpty(source)) return "";
        return source
                .replaceAll("(?i)^\\s*from\\s*", "")
                .replaceAll("[\\s\\p{Punct}，。；：、【】《》“”‘’（）]+", "")
                .toLowerCase();
    }

    public static boolean hasDanmakuSource(DanmakuItem item) {
        return item != null && !TextUtils.isEmpty(normalizeSourceKey(item.getFrom()));
    }

    public static boolean isSameDanmakuSource(DanmakuItem item, DanmakuItem reference) {
        String referenceSource = reference != null ? normalizeSourceKey(reference.getFrom()) : "";
        if (TextUtils.isEmpty(referenceSource)) return true;
        String itemSource = item != null ? normalizeSourceKey(item.getFrom()) : "";
        return referenceSource.equals(itemSource);
    }

    public static String getDisplaySource(DanmakuItem item) {
        if (item == null || TextUtils.isEmpty(item.getFrom())) return "默认";
        return item.getFrom();
    }

    public static DanmakuItem getLastDanmakuItem() {
        if (lastDanmakuUrl != null && !lastDanmakuUrl.isEmpty()) {
            DanmakuItem item = lastDanmakuUrlItemMap.get(lastDanmakuUrl);
            if (item != null) return item;

            for (DanmakuItem cachedItem : lastDanmakuUrlItemMap.values()) {
                if (cachedItem != null && lastDanmakuUrl.equals(cachedItem.getDanmakuUrl())) {
                    return cachedItem;
                }
            }

            for (DanmakuItem cachedItem : lastDanmakuItemMap.values()) {
                if (cachedItem != null && lastDanmakuUrl.equals(cachedItem.getDanmakuUrl())) {
                    return cachedItem;
                }
            }
        }

        if (lastDanmakuId > 0) {
            DanmakuItem item = lastDanmakuItemMap.get(lastDanmakuId);
            if (item != null) return item;
        }

        return null;
    }

    private static DanmakuItem findContinuationItemById(int epId, DanmakuItem reference) {
        DanmakuItem item = lastDanmakuItemMap.get(epId);
        if (isSameContinuationItem(item, reference)) return item;

        for (DanmakuItem candidate : lastDanmakuItemMap.values()) {
            if (candidate != null && candidate.getEpId() != null && candidate.getEpId() == epId
                    && isSameContinuationItem(candidate, reference)) {
                return candidate;
            }
        }

        for (DanmakuItem candidate : seriesDanmakuCache.values()) {
            if (candidate != null && candidate.getEpId() != null && candidate.getEpId() == epId
                    && isSameContinuationItem(candidate, reference)) {
                return candidate;
            }
        }

        if (item != null && reference != null) {
            DanmakuSpider.log("🧭 ID位移候选来源不一致，跳过: 上一集="
                    + getDisplaySource(reference) + "，候选=" + getDisplaySource(item));
        }
        return null;
    }

    private static boolean isSameContinuationItem(DanmakuItem item, DanmakuItem reference) {
        if (item == null) return false;
        if (reference == null) return true;
        if (!TextUtils.isEmpty(reference.getApiBase()) && !reference.getApiBase().equals(item.getApiBase())) {
            return false;
        }
        return isSameDanmakuSource(item, reference);
    }

    public static void resetAutoSearch() {
        hasAutoSearched = false;
        lastProcessedTitle = "";
        currentVideoSignature = "";
        lastVideoDetectedTime = 0;
        lastDanmakuId = -1;
        lastAutoDanmakuUrl = "";
        lastManualDanmakuUrl = "";
        lastDanmakuUrl = "";
        lastDanmakuItemMap.clear();
        lastDanmakuUrlItemMap.clear();
        seriesDanmakuCache.clear();
    }
}
