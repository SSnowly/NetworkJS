package hu.snowylol.networkjs;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

public class NetworkJS {
    public static final String MODID = "networkjs";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static void init() {
        LOGGER.info("NetworkJS initialized - welcome to java");
    }
}