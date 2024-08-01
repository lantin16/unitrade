package com.lantin.unitrade.constant;

/**
 * Redis相关常量
 * @Author lantin
 * @Date 2024/7/30
 */

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";

    // 用户token过期时间（开发测试时设置成了10天）
    // public static final Long LOGIN_USER_TTL = 30L;
    public static final Long LOGIN_USER_TTL = 14400L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_ITEM_TTL = 30L;
    public static final String CACHE_ITEM_KEY = "cache:item:";
    public static final Long CACHE_CART_TTL = 3L;
    public static final String CACHE_CART_KEY = "cache:cart:";
    public static final Long CACHE_SHOP_TYPE_TTL = 30L;
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop:type";

    public static final String LOCK_ITEM_KEY = "lock:item:";
    public static final String LOCK_ORDER_KEY = "lock:order:";
    public static final String LOCK_GEN_ORDER_KEY = "lock:order";
    public static final String LOCK_KEY_PREFIX = "lock:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String ITEM_STOCK_KEY = "item:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String INCREMENT_ID_KEY = "icr:";
    public static final String ORDER_PREFIX = "order:";
    public static final String FOLLOWS_KEY = "follows:";
    // 滚动分页的pageSize
    public static final Long SCORE_PAGE_SIZE = 2L;


}
