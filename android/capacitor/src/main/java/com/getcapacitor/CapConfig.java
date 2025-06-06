package com.getcapacitor;

import static com.getcapacitor.Bridge.CAPACITOR_HTTPS_SCHEME;
import static com.getcapacitor.Bridge.DEFAULT_ANDROID_WEBVIEW_VERSION;
import static com.getcapacitor.Bridge.DEFAULT_HUAWEI_WEBVIEW_VERSION;
import static com.getcapacitor.Bridge.MINIMUM_ANDROID_WEBVIEW_VERSION;
import static com.getcapacitor.Bridge.MINIMUM_HUAWEI_WEBVIEW_VERSION;
import static com.getcapacitor.FileUtils.readFileFromAssets;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import androidx.annotation.Nullable;
import com.getcapacitor.util.JSONUtils;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents the configuration options for Capacitor
 */
public class CapConfig {

    private static final String LOG_BEHAVIOR_NONE = "none";
    private static final String LOG_BEHAVIOR_DEBUG = "debug";
    private static final String LOG_BEHAVIOR_PRODUCTION = "production";

    // Server Config
    private boolean html5mode = true;
    private String serverUrl;
    private String hostname = "localhost";
    private String androidScheme = CAPACITOR_HTTPS_SCHEME;
    private String[] allowNavigation;

    // Android Config
    private String overriddenUserAgentString;
    private String appendedUserAgentString;
    private String backgroundColor;
    private boolean allowMixedContent = false;
    private boolean captureInput = false;
    private boolean webContentsDebuggingEnabled = false;
    private boolean loggingEnabled = true;
    private boolean initialFocus = true;
    private boolean useLegacyBridge = false;
    private int minWebViewVersion = DEFAULT_ANDROID_WEBVIEW_VERSION;
    private int minHuaweiWebViewVersion = DEFAULT_HUAWEI_WEBVIEW_VERSION;
    private String errorPath;
    private boolean zoomableWebView = false;
    private boolean resolveServiceWorkerRequests = true;
    private String adjustMarginsForEdgeToEdge = "disable";

    // Embedded
    private String startPath;

    // Plugins
    private Map<String, PluginConfig> pluginsConfiguration = null;

    // Config Object JSON (legacy)
    private JSONObject configJSON = new JSONObject();

    /**
     * Constructs an empty config file.
     */
    private CapConfig() {}

    /**
     * Get an instance of the Config file object.
     * @deprecated use {@link #loadDefault(Context)} to load an instance of the Config object
     * from the capacitor.config.json file, or use the {@link CapConfig.Builder} to construct
     * a CapConfig for embedded use.
     *
     * @param assetManager The AssetManager used to load the config file
     * @param config JSON describing a configuration to use
     */
    @Deprecated
    public CapConfig(AssetManager assetManager, JSONObject config) {
        if (config != null) {
            this.configJSON = config;
        } else {
            // Load the capacitor.config.json
            loadConfigFromAssets(assetManager, null);
        }

        deserializeConfig(null);
    }

    /**
     * Constructs a Capacitor Configuration from config.json file.
     *
     * @param context The context.
     * @return A loaded config file, if successful.
     */
    public static CapConfig loadDefault(Context context) {
        CapConfig config = new CapConfig();

        if (context == null) {
            Logger.error("Capacitor Config could not be created from file. Context must not be null.");
            return config;
        }

        config.loadConfigFromAssets(context.getAssets(), null);
        config.deserializeConfig(context);
        return config;
    }

    /**
     * Constructs a Capacitor Configuration from config.json file within the app assets.
     *
     * @param context The context.
     * @param path A path relative to the root assets directory.
     * @return A loaded config file, if successful.
     */
    public static CapConfig loadFromAssets(Context context, String path) {
        CapConfig config = new CapConfig();

        if (context == null) {
            Logger.error("Capacitor Config could not be created from file. Context must not be null.");
            return config;
        }

        config.loadConfigFromAssets(context.getAssets(), path);
        config.deserializeConfig(context);
        return config;
    }

    /**
     * Constructs a Capacitor Configuration from config.json file within the app file-space.
     *
     * @param context The context.
     * @param path A path relative to the root of the app file-space.
     * @return A loaded config file, if successful.
     */
    public static CapConfig loadFromFile(Context context, String path) {
        CapConfig config = new CapConfig();

        if (context == null) {
            Logger.error("Capacitor Config could not be created from file. Context must not be null.");
            return config;
        }

        config.loadConfigFromFile(path);
        config.deserializeConfig(context);
        return config;
    }

    /**
     * Constructs a Capacitor Configuration using ConfigBuilder.
     *
     * @param builder A config builder initialized with values
     */
    private CapConfig(Builder builder) {
        // Server Config
        this.html5mode = builder.html5mode;
        this.serverUrl = builder.serverUrl;
        this.hostname = builder.hostname;

        if (this.validateScheme(builder.androidScheme)) {
            this.androidScheme = builder.androidScheme;
        }

        this.allowNavigation = builder.allowNavigation;

        // Android Config
        this.overriddenUserAgentString = builder.overriddenUserAgentString;
        this.appendedUserAgentString = builder.appendedUserAgentString;
        this.backgroundColor = builder.backgroundColor;
        this.allowMixedContent = builder.allowMixedContent;
        this.captureInput = builder.captureInput;
        this.webContentsDebuggingEnabled = builder.webContentsDebuggingEnabled;
        this.loggingEnabled = builder.loggingEnabled;
        this.initialFocus = builder.initialFocus;
        this.useLegacyBridge = builder.useLegacyBridge;
        this.minWebViewVersion = builder.minWebViewVersion;
        this.minHuaweiWebViewVersion = builder.minHuaweiWebViewVersion;
        this.errorPath = builder.errorPath;
        this.zoomableWebView = builder.zoomableWebView;
        this.resolveServiceWorkerRequests = builder.resolveServiceWorkerRequests;
        this.adjustMarginsForEdgeToEdge = builder.adjustMarginsForEdgeToEdge;

        // Embedded
        this.startPath = builder.startPath;

        // Plugins Config
        this.pluginsConfiguration = builder.pluginsConfiguration;
    }

    /**
     * Loads a Capacitor Configuration JSON file into a Capacitor Configuration object.
     * An optional path string can be provided to look for the config in a subdirectory path.
     */
    private void loadConfigFromAssets(AssetManager assetManager, String path) {
        if (path == null) {
            path = "";
        } else {
            // Add slash at the end to form a proper file path if going deeper in assets dir
            if (path.charAt(path.length() - 1) != '/') {
                path = path + "/";
            }
        }

        try {
            String jsonString = readFileFromAssets(assetManager, path + "capacitor.config.json");
            configJSON = new JSONObject(jsonString);
        } catch (IOException ex) {
            Logger.error("Unable to load capacitor.config.json. Run npx cap copy first", ex);
        } catch (JSONException ex) {
            Logger.error("Unable to parse capacitor.config.json. Make sure it's valid json", ex);
        }
    }

    /**
     * Loads a Capacitor Configuration JSON file into a Capacitor Configuration object.
     * An optional path string can be provided to look for the config in a subdirectory path.
     */
    private void loadConfigFromFile(String path) {
        if (path == null) {
            path = "";
        } else {
            // Add slash at the end to form a proper file path if going deeper in assets dir
            if (path.charAt(path.length() - 1) != '/') {
                path = path + "/";
            }
        }

        try {
            File configFile = new File(path + "capacitor.config.json");
            String jsonString = FileUtils.readFileFromDisk(configFile);
            configJSON = new JSONObject(jsonString);
        } catch (JSONException ex) {
            Logger.error("Unable to parse capacitor.config.json. Make sure it's valid json", ex);
        } catch (IOException ex) {
            Logger.error("Unable to load capacitor.config.json.", ex);
        }
    }

    /**
     * Deserializes the config from JSON into a Capacitor Configuration object.
     */
    private void deserializeConfig(@Nullable Context context) {
        boolean isDebug = context != null && (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

        // Server
        html5mode = JSONUtils.getBoolean(configJSON, "server.html5mode", html5mode);
        serverUrl = JSONUtils.getString(configJSON, "server.url", null);
        hostname = JSONUtils.getString(configJSON, "server.hostname", hostname);
        errorPath = JSONUtils.getString(configJSON, "server.errorPath", null);
        startPath = JSONUtils.getString(configJSON, "server.appStartPath", null);

        String configSchema = JSONUtils.getString(configJSON, "server.androidScheme", androidScheme);
        if (this.validateScheme(configSchema)) {
            androidScheme = configSchema;
        }

        allowNavigation = JSONUtils.getArray(configJSON, "server.allowNavigation", null);

        // Android
        overriddenUserAgentString = JSONUtils.getString(
            configJSON,
            "android.overrideUserAgent",
            JSONUtils.getString(configJSON, "overrideUserAgent", null)
        );
        appendedUserAgentString = JSONUtils.getString(
            configJSON,
            "android.appendUserAgent",
            JSONUtils.getString(configJSON, "appendUserAgent", null)
        );
        backgroundColor = JSONUtils.getString(
            configJSON,
            "android.backgroundColor",
            JSONUtils.getString(configJSON, "backgroundColor", null)
        );
        allowMixedContent = JSONUtils.getBoolean(
            configJSON,
            "android.allowMixedContent",
            JSONUtils.getBoolean(configJSON, "allowMixedContent", allowMixedContent)
        );
        minWebViewVersion = JSONUtils.getInt(configJSON, "android.minWebViewVersion", DEFAULT_ANDROID_WEBVIEW_VERSION);
        minHuaweiWebViewVersion = JSONUtils.getInt(configJSON, "android.minHuaweiWebViewVersion", DEFAULT_HUAWEI_WEBVIEW_VERSION);
        captureInput = JSONUtils.getBoolean(configJSON, "android.captureInput", captureInput);
        useLegacyBridge = JSONUtils.getBoolean(configJSON, "android.useLegacyBridge", useLegacyBridge);
        webContentsDebuggingEnabled = JSONUtils.getBoolean(configJSON, "android.webContentsDebuggingEnabled", isDebug);
        zoomableWebView = JSONUtils.getBoolean(configJSON, "android.zoomEnabled", JSONUtils.getBoolean(configJSON, "zoomEnabled", false));
        resolveServiceWorkerRequests = JSONUtils.getBoolean(configJSON, "android.resolveServiceWorkerRequests", true);
        adjustMarginsForEdgeToEdge = JSONUtils.getString(configJSON, "android.adjustMarginsForEdgeToEdge", "disable");

        String logBehavior = JSONUtils.getString(
            configJSON,
            "android.loggingBehavior",
            JSONUtils.getString(configJSON, "loggingBehavior", LOG_BEHAVIOR_DEBUG)
        );
        switch (logBehavior.toLowerCase(Locale.ROOT)) {
            case LOG_BEHAVIOR_PRODUCTION:
                loggingEnabled = true;
                break;
            case LOG_BEHAVIOR_NONE:
                loggingEnabled = false;
                break;
            default: // LOG_BEHAVIOR_DEBUG
                loggingEnabled = isDebug;
        }

        initialFocus = JSONUtils.getBoolean(
            configJSON,
            "android.initialFocus",
            JSONUtils.getBoolean(configJSON, "initialFocus", initialFocus)
        );

        // Plugins
        pluginsConfiguration = deserializePluginsConfig(JSONUtils.getObject(configJSON, "plugins"));
    }

    private boolean validateScheme(String scheme) {
        List<String> invalidSchemes = Arrays.asList("file", "ftp", "ftps", "ws", "wss", "about", "blob", "data");
        if (invalidSchemes.contains(scheme)) {
            Logger.warn(scheme + " is not an allowed scheme.  Defaulting to https.");
            return false;
        }

        // Non-http(s) schemes are not allowed to modify the URL path as of Android Webview 117
        if (!scheme.equals("http") && !scheme.equals("https")) {
            Logger.warn(
                "Using a non-standard scheme: " + scheme + " for Android. This is known to cause issues as of Android Webview 117."
            );
        }

        return true;
    }

    public boolean isHTML5Mode() {
        return html5mode;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getErrorPath() {
        return errorPath;
    }

    public String getHostname() {
        return hostname;
    }

    public String getStartPath() {
        return startPath;
    }

    public String getAndroidScheme() {
        return androidScheme;
    }

    public String[] getAllowNavigation() {
        return allowNavigation;
    }

    public String getOverriddenUserAgentString() {
        return overriddenUserAgentString;
    }

    public String getAppendedUserAgentString() {
        return appendedUserAgentString;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public boolean isMixedContentAllowed() {
        return allowMixedContent;
    }

    public boolean isInputCaptured() {
        return captureInput;
    }

    public boolean isResolveServiceWorkerRequests() {
        return resolveServiceWorkerRequests;
    }

    public boolean isWebContentsDebuggingEnabled() {
        return webContentsDebuggingEnabled;
    }

    public boolean isZoomableWebView() {
        return zoomableWebView;
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public boolean isInitialFocus() {
        return initialFocus;
    }

    public boolean isUsingLegacyBridge() {
        return useLegacyBridge;
    }

    public String adjustMarginsForEdgeToEdge() {
        return adjustMarginsForEdgeToEdge;
    }

    public int getMinWebViewVersion() {
        if (minWebViewVersion < MINIMUM_ANDROID_WEBVIEW_VERSION) {
            Logger.warn("Specified minimum webview version is too low, defaulting to " + MINIMUM_ANDROID_WEBVIEW_VERSION);
            return MINIMUM_ANDROID_WEBVIEW_VERSION;
        }

        return minWebViewVersion;
    }

    public int getMinHuaweiWebViewVersion() {
        if (minHuaweiWebViewVersion < MINIMUM_HUAWEI_WEBVIEW_VERSION) {
            Logger.warn("Specified minimum Huawei webview version is too low, defaulting to " + MINIMUM_HUAWEI_WEBVIEW_VERSION);
            return MINIMUM_HUAWEI_WEBVIEW_VERSION;
        }

        return minHuaweiWebViewVersion;
    }

    public PluginConfig getPluginConfiguration(String pluginId) {
        PluginConfig pluginConfig = pluginsConfiguration.get(pluginId);
        if (pluginConfig == null) {
            pluginConfig = new PluginConfig(new JSONObject());
        }

        return pluginConfig;
    }

    /**
     * Get a JSON object value from the Capacitor config.
     * @deprecated use {@link PluginConfig#getObject(String)}  to access plugin config values.
     * For main Capacitor config values, use the appropriate getter.
     *
     * @param key A key to fetch from the config
     * @return The value from the config, if exists. Null if not
     */
    @Deprecated
    public JSONObject getObject(String key) {
        try {
            return configJSON.getJSONObject(key);
        } catch (Exception ex) {}
        return null;
    }

    /**
     * Get a string value from the Capacitor config.
     * @deprecated use {@link PluginConfig#getString(String, String)} to access plugin config
     * values. For main Capacitor config values, use the appropriate getter.
     *
     * @param key A key to fetch from the config
     * @return The value from the config, if exists. Null if not
     */
    @Deprecated
    public String getString(String key) {
        return JSONUtils.getString(configJSON, key, null);
    }

    /**
     * Get a string value from the Capacitor config.
     * @deprecated use {@link PluginConfig#getString(String, String)} to access plugin config
     * values. For main Capacitor config values, use the appropriate getter.
     *
     * @param key A key to fetch from the config
     * @param defaultValue A default value to return if the key does not exist in the config
     * @return The value from the config, if key exists. Default value returned if not
     */
    @Deprecated
    public String getString(String key, String defaultValue) {
        return JSONUtils.getString(configJSON, key, defaultValue);
    }

    /**
     * Get a boolean value from the Capacitor config.
     * @deprecated use {@link PluginConfig#getBoolean(String, boolean)} to access plugin config
     * values. For main Capacitor config values, use the appropriate getter.
     *
     * @param key A key to fetch from the config
     * @param defaultValue A default value to return if the key does not exist in the config
     * @return The value from the config, if key exists. Default value returned if not
     */
    @Deprecated
    public boolean getBoolean(String key, boolean defaultValue) {
        return JSONUtils.getBoolean(configJSON, key, defaultValue);
    }

    /**
     * Get an integer value from the Capacitor config.
     * @deprecated use {@link PluginConfig#getInt(String, int)}  to access the plugin config
     * values. For main Capacitor config values, use the appropriate getter.
     *
     * @param key A key to fetch from the config
     * @param defaultValue A default value to return if the key does not exist in the config
     * @return The value from the config, if key exists. Default value returned if not
     */
    @Deprecated
    public int getInt(String key, int defaultValue) {
        return JSONUtils.getInt(configJSON, key, defaultValue);
    }

    /**
     * Get a string array value from the Capacitor config.
     * @deprecated use {@link PluginConfig#getArray(String)}  to access the plugin config
     * values. For main Capacitor config values, use the appropriate getter.
     *
     * @param key A key to fetch from the config
     * @return The value from the config, if exists. Null if not
     */
    @Deprecated
    public String[] getArray(String key) {
        return JSONUtils.getArray(configJSON, key, null);
    }

    /**
     * Get a string array value from the Capacitor config.
     * @deprecated use {@link PluginConfig#getArray(String, String[])}  to access the plugin
     * config values. For main Capacitor config values, use the appropriate getter.
     *
     * @param key A key to fetch from the config
     * @param defaultValue A default value to return if the key does not exist in the config
     * @return The value from the config, if key exists. Default value returned if not
     */
    @Deprecated
    public String[] getArray(String key, String[] defaultValue) {
        return JSONUtils.getArray(configJSON, key, defaultValue);
    }

    private static Map<String, PluginConfig> deserializePluginsConfig(JSONObject pluginsConfig) {
        Map<String, PluginConfig> pluginsMap = new HashMap<>();

        // return an empty map if there is no pluginsConfig json
        if (pluginsConfig == null) {
            return pluginsMap;
        }

        Iterator<String> pluginIds = pluginsConfig.keys();

        while (pluginIds.hasNext()) {
            String pluginId = pluginIds.next();
            JSONObject value = null;

            try {
                value = pluginsConfig.getJSONObject(pluginId);
                PluginConfig pluginConfig = new PluginConfig(value);
                pluginsMap.put(pluginId, pluginConfig);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return pluginsMap;
    }

    /**
     * Builds a Capacitor Configuration in code
     */
    public static class Builder {

        private Context context;

        // Server Config Values
        private boolean html5mode = true;
        private String serverUrl;
        private String errorPath;
        private String hostname = "localhost";
        private String androidScheme = CAPACITOR_HTTPS_SCHEME;
        private String[] allowNavigation;

        // Android Config Values
        private String overriddenUserAgentString;
        private String appendedUserAgentString;
        private String backgroundColor;
        private boolean allowMixedContent = false;
        private boolean captureInput = false;
        private Boolean webContentsDebuggingEnabled = null;
        private boolean loggingEnabled = true;
        private boolean initialFocus = false;
        private boolean useLegacyBridge = false;
        private int minWebViewVersion = DEFAULT_ANDROID_WEBVIEW_VERSION;
        private int minHuaweiWebViewVersion = DEFAULT_HUAWEI_WEBVIEW_VERSION;
        private boolean zoomableWebView = false;
        private boolean resolveServiceWorkerRequests = true;
        private String adjustMarginsForEdgeToEdge = "disable";

        // Embedded
        private String startPath = null;

        // Plugins Config Object
        private Map<String, PluginConfig> pluginsConfiguration = new HashMap<>();

        /**
         * Constructs a new CapConfig Builder.
         *
         * @param context The context
         */
        public Builder(Context context) {
            this.context = context;
        }

        /**
         * Builds a Capacitor Config from the builder.
         *
         * @return A new Capacitor Config
         */
        public CapConfig create() {
            if (webContentsDebuggingEnabled == null) {
                webContentsDebuggingEnabled = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            }

            return new CapConfig(this);
        }

        public Builder setPluginsConfiguration(JSONObject pluginsConfiguration) {
            this.pluginsConfiguration = deserializePluginsConfig(pluginsConfiguration);
            return this;
        }

        public Builder setHTML5mode(boolean html5mode) {
            this.html5mode = html5mode;
            return this;
        }

        public Builder setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public Builder setErrorPath(String errorPath) {
            this.errorPath = errorPath;
            return this;
        }

        public Builder setHostname(String hostname) {
            this.hostname = hostname;
            return this;
        }

        public Builder setStartPath(String path) {
            this.startPath = path;
            return this;
        }

        public Builder setAndroidScheme(String androidScheme) {
            this.androidScheme = androidScheme;
            return this;
        }

        public Builder setAllowNavigation(String[] allowNavigation) {
            this.allowNavigation = allowNavigation;
            return this;
        }

        public Builder setOverriddenUserAgentString(String overriddenUserAgentString) {
            this.overriddenUserAgentString = overriddenUserAgentString;
            return this;
        }

        public Builder setAppendedUserAgentString(String appendedUserAgentString) {
            this.appendedUserAgentString = appendedUserAgentString;
            return this;
        }

        public Builder setBackgroundColor(String backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public Builder setAllowMixedContent(boolean allowMixedContent) {
            this.allowMixedContent = allowMixedContent;
            return this;
        }

        public Builder setCaptureInput(boolean captureInput) {
            this.captureInput = captureInput;
            return this;
        }

        public Builder setUseLegacyBridge(boolean useLegacyBridge) {
            this.useLegacyBridge = useLegacyBridge;
            return this;
        }

        public Builder setResolveServiceWorkerRequests(boolean resolveServiceWorkerRequests) {
            this.resolveServiceWorkerRequests = resolveServiceWorkerRequests;
            return this;
        }

        public Builder setWebContentsDebuggingEnabled(boolean webContentsDebuggingEnabled) {
            this.webContentsDebuggingEnabled = webContentsDebuggingEnabled;
            return this;
        }

        public Builder setZoomableWebView(boolean zoomableWebView) {
            this.zoomableWebView = zoomableWebView;
            return this;
        }

        public Builder setLoggingEnabled(boolean enabled) {
            this.loggingEnabled = enabled;
            return this;
        }

        public Builder setInitialFocus(boolean focus) {
            this.initialFocus = focus;
            return this;
        }
    }
}
