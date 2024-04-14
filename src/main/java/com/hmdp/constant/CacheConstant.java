package com.hmdp.constant;

import java.time.Duration;

/**
 * @Author zwf
 * @date 2024/3/11 19:20
 */
public class CacheConstant {
    public static final Long CACHE_INEXIST_SHOP_TTL = 3L;

    public static final String CACHE_SHOP_PREFIX="cache:shop:";

    public static final String CACHE_SHOP_LIST_PREFIX="cache:shoplist:";

    public static final Long CACHE_SHOP_TTL=30L;

    public static final String LOCK_SHOP_PREFIX="lock:shop:";

    public static final String BLOG_LIKE_PREFIX="blog:like:";

    public static final String LOCK_ORDER_PREFIX="lock:order:";

    public static final String FOLLOW_PREFIX = "follow:";
}
