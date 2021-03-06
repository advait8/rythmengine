/**
 * Copyright (C) 2013-2016 The Rythm Engine project
 * for LICENSE and other details see:
 * https://github.com/rythmengine/rythmengine
 */
package org.rythmengine.conf;

import org.rythmengine.RythmEngine;
import org.rythmengine.extension.*;
import org.rythmengine.logger.ILogger;
import org.rythmengine.logger.Logger;
import org.rythmengine.resource.ITemplateResource;
import org.rythmengine.utils.RawData;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

import static org.rythmengine.conf.RythmConfigurationKey.*;

/**
 * Store the configuration for a {@link org.rythmengine.RythmEngine rythm engine}
 * instance. Different engine instance has different configuration instance.
 */
public class RythmConfiguration {
    private Map<String, Object> raw;
    private Map<RythmConfigurationKey, Object> data;
    private RythmEngine engine;

    /**
     * Construct a <code>RythmConfiguration</code> with a map. The map is copied to
     * the original map of the configuration instance
     *
     * @param configuration
     */
    public RythmConfiguration(Map<String, ?> configuration, RythmEngine engine) {
        raw = new HashMap<String, Object>(configuration);
        data = new HashMap<RythmConfigurationKey, Object>(configuration.size());
        this.engine = engine;
    }

    /**
     * Return configuration by {@link RythmConfigurationKey configuration key}
     *
     * @param key
     * @param <T>
     * @return the configured item
     */
    public <T> T get(RythmConfigurationKey key) {
        Object o = data.get(key);
        if (null == o) {
            o = key.getConfiguration(raw);
            if (null != o) {
                data.put(key, o);
            } else {
                data.put(key, RawData.NULL);
            }
        }
        if (o == RawData.NULL) {
            return null;
        } else {
            return (T) o;
        }
    }

    /**
     * Return a configuration value as list
     *
     * @param key
     * @param c
     * @param <T>
     * @return the list
     */
    public <T> List<T> getList(RythmConfigurationKey key, Class<T> c) {
        Object o = data.get(key);
        if (null == o) {
            List<T> l = RythmConfigurationKey.getImplList(key.getKey(), raw, c);
            data.put(key, l);
            return l;
        } else {
            return (List) o;
        }
    }

    /**
     * Look up configuration by a <code>String<code/> key. If the String key
     * can be converted into {@link RythmConfigurationKey rythm configuration key}, then
     * it is converted and call to {@link #get(RythmConfigurationKey)} method. Otherwise
     * the original configuration map is used to fetch the value from the string key
     *
     * @param key
     * @param <T>
     * @return the configured item
     */
    public <T> T get(String key) {
        if (key.startsWith("rythm.")) {
            key = key.replaceFirst("rythm.", "");
        }
        RythmConfigurationKey rk = RythmConfigurationKey.valueOfIgnoreCase(key);
        if (null != rk) {
            return get(rk);
        } else {
            return (T) raw.get(key);
        }
    }

    // speed up access to frequently accessed and non-modifiable configuration items
    private String _pluginVersion = null;

    /**
     * Return {@link RythmConfigurationKey#ENGINE_PLUGIN_VERSION plugin version} without lookup
     *
     * @return plugin version
     */
    public String pluginVersion() {
        if (null == _pluginVersion) {
            _pluginVersion = get(ENGINE_PLUGIN_VERSION);
        }
        return _pluginVersion;
    }

    private IByteCodeHelper _byteCodeHelper = null;

    /**
     * Return {@link RythmConfigurationKey#ENGINE_CLASS_LOADER_BYTE_CODE_HELPER_IMPL} without lookup
     *
     * @return the byte code helper
     */
    public IByteCodeHelper byteCodeHelper() {
        if (null == _byteCodeHelper) {
            _byteCodeHelper = get(ENGINE_CLASS_LOADER_BYTE_CODE_HELPER_IMPL);
        }
        return _byteCodeHelper;
    }

    private Boolean _play = null;

    /**
     * Return {@link RythmConfigurationKey#ENGINE_PLAYFRAMEWORK} without lookup
     *
     * @return true if the engine is used by playframework
     */
    public boolean playFramework() {
        if (null == _play) {
            _play = get(ENGINE_PLAYFRAMEWORK);
        }
        return _play;
    }

    private Boolean _logRenderTime = null;

    /**
     * Return {@link RythmConfigurationKey#LOG_TIME_RENDER_ENABLED} without
     * look up
     *
     * @return true if enable log render time
     */
    public boolean logRenderTime() {
        if (null == _logRenderTime) {
            _logRenderTime = get(LOG_TIME_RENDER_ENABLED);
        }
        return _logRenderTime;
    }

    private Boolean _loadPrecompiled = null;

    /**
     * Return {@link RythmConfigurationKey#ENGINE_LOAD_PRECOMPILED_ENABLED}
     * without lookup
     *
     * @return true if load precompiled
     */
    public boolean loadPrecompiled() {
        if (null == _loadPrecompiled) {
            Boolean b = get(ENGINE_LOAD_PRECOMPILED_ENABLED);
            _loadPrecompiled = b || gae();
        }
        return _loadPrecompiled;
    }

    private Boolean _precompileMode = null;

    /**
     * Return {@link RythmConfigurationKey#ENGINE_PRECOMPILE_MODE} without lookup
     *
     * @return true if precompiling
     */
    public boolean precompileMode() {
        if (null == _precompileMode) {
            _precompileMode = get(ENGINE_PRECOMPILE_MODE);
        }
        return _precompileMode;
    }

    private Boolean _disableFileWrite = null;

    /**
     * Return inversed value of {@link RythmConfigurationKey#ENGINE_FILE_WRITE_ENABLED}
     * without lookup
     *
     * @return true if file write is disabled
     */
    public boolean disableFileWrite() {
        if (null == _disableFileWrite) {
            boolean b = (Boolean) get(ENGINE_FILE_WRITE_ENABLED);
            b = b && !gae();
            _disableFileWrite = !b;
        }
        return _disableFileWrite;
    }

    private Boolean _gae = null;

    private static boolean isGaeSdkInClasspath() {
        try {
            Class.forName("com.google.appengine.api.LifecycleManager");
            return true;
        } catch (Throwable t) {
            // Nothing to do
        }
        return false;
    }

    public boolean gae() {
        if (null == _gae) {
            boolean b = isGaeSdkInClasspath();
            if (b) {
                try {
                    Class clz = Class.forName("org.rythmengine.conf.GAEDetectorImpl");
                    GAEDetector detector = (GAEDetector)clz.newInstance();
                    b = detector.isInGaeCloud();
                } catch (Throwable t) {
                    logger.warn(t, "error detecting gae environment");
                    b = false;
                }
            }
            _gae = b;
        }
        return _gae;
    }

    private Set<String> _restrictedClasses = null;

    /**
     * Return {@link RythmConfigurationKey#SANDBOX_RESTRICTED_CLASS} without lookup
     * <p/>
     * <p>Note, the return value also contains rythm's built-in restricted classes</p>
     *
     * @return a set of restricted classes
     */
    public Set<String> restrictedClasses() {
        if (null == _restrictedClasses) {
            String s = get(SANDBOX_RESTRICTED_CLASS);
            s += ";org.rythmengine.Rythm;RythmEngine;RythmSecurityManager,java.io;java.nio;java.security;java.rmi;java.net;java.awt;java.applet";
            _restrictedClasses = new HashSet<String>();
            for (String cls : Arrays.asList(s.split(";"))) {
                cls = cls.trim();
                if ("".equals(cls)) {
                    continue;
                }
                _restrictedClasses.add(cls);
            }
        }
        return new HashSet<String>(_restrictedClasses);
    }

    private Boolean _enableTypeInference = null;

    /**
     * Get {@link RythmConfigurationKey#FEATURE_TYPE_INFERENCE_ENABLED} without
     * lookup
     *
     * @return true if type inference is enabled
     */
    public boolean typeInferenceEnabled() {
        if (null == _enableTypeInference) {
            _enableTypeInference = get(FEATURE_TYPE_INFERENCE_ENABLED);
        }
        return _enableTypeInference;
    }

    private Boolean _smartEscapeEnabled = null;

    /**
     * Get {@link RythmConfigurationKey#FEATURE_SMART_ESCAPE_ENABLED} without lookup
     *
     * @return true if smart escape is enabled
     */
    public boolean smartEscapeEnabled() {
        if (null == _smartEscapeEnabled) {
            _smartEscapeEnabled = (Boolean) get(FEATURE_SMART_ESCAPE_ENABLED);
        }
        return _smartEscapeEnabled;
    }

    private Boolean _naturalTemplateEnabled = null;

    /**
     * Get {@link RythmConfigurationKey#FEATURE_NATURAL_TEMPLATE_ENABLED} without lookup
     *
     * @return true if natural template is enabled
     */
    public boolean naturalTemplateEnabled() {
        if (null == _naturalTemplateEnabled) {
            _naturalTemplateEnabled = (Boolean) get(FEATURE_NATURAL_TEMPLATE_ENABLED);
        }
        return _naturalTemplateEnabled;
    }

    private Boolean _debugJavaSourceEnabled = null;

    /**
     * Get {@link RythmConfigurationKey#ENGINE_OUTPUT_JAVA_SOURCE_ENABLED} without lookup
     *
     * @return true if debug java source is enabled
     */
    public boolean debugJavaSourceEnabled() {
        if (null == _debugJavaSourceEnabled) {
            _debugJavaSourceEnabled = (Boolean) get(ENGINE_OUTPUT_JAVA_SOURCE_ENABLED);
        }
        return _debugJavaSourceEnabled;
    }

    private Boolean _cacheEnabled = null;

    /**
     * Return true if cache is not disabled for the engine instance. A cache is disabled when
     * <ul>
     * <li>{@link RythmConfigurationKey#CACHE_ENABLED} is <code>true</code> or</li>
     * <li>{@link RythmConfigurationKey#CACHE_PROD_ONLY_ENABLED} is <code>true</code> and
     * {@link RythmConfigurationKey#ENGINE_MODE} is {@link org.rythmengine.Rythm.Mode#dev}</li>
     * </ul>
     *
     * @return true if cache enabled
     */
    public boolean cacheEnabled() {
        if (this == EMPTY_CONF) return false;
        if (null == _cacheEnabled) {
            Boolean ce = get(CACHE_ENABLED);
            if (!ce) {
                _cacheEnabled = false;
            } else {
                if (engine.isProdMode()) {
                    _cacheEnabled = true;
                } else {
                    Boolean po = get(CACHE_PROD_ONLY_ENABLED);
                    _cacheEnabled = !po;
                }
            }
        }
        return _cacheEnabled;
    }

    /**
     * Return true if cache is disabled for the engine instance.
     *
     * @return false if cache enabled
     * @see #cacheEnabled()
     */
    public boolean cacheDisabled() {
        return !cacheEnabled();
    }

    private Boolean _transformEnabled = null;

    /**
     * Return {@link RythmConfigurationKey#FEATURE_TRANSFORM_ENABLED} without look up
     *
     * @return true if transform enabled
     */
    public boolean transformEnabled() {
        if (null == _transformEnabled) {
            _transformEnabled = get(FEATURE_TRANSFORM_ENABLED);
        }
        return _transformEnabled;
    }

    private Boolean _compactEnabled = null;


    /**
     * Return {@link RythmConfigurationKey#CODEGEN_COMPACT_ENABLED} without look up
     *
     * @return true if compact mode is enabled
     */
    public boolean compactModeEnabled() {
        if (null == _compactEnabled) {
            _compactEnabled = get(CODEGEN_COMPACT_ENABLED);
        }
        return _compactEnabled;
    }

    private IDurationParser _durationParser = null;

    /**
     * Return {@link RythmConfigurationKey#CACHE_DURATION_PARSER_IMPL} without lookup
     *
     * @return the duration parser implementation
     */
    public IDurationParser durationParser() {
        if (null == _durationParser) {
            _durationParser = get(CACHE_DURATION_PARSER_IMPL);
        }
        return _durationParser;
    }

    private ICodeType _defaultCodeType = null;

    /**
     * Return {@link RythmConfigurationKey#DEFAULT_CODE_TYPE_IMPL} without lookup
     *
     * @return default code type
     */
    public ICodeType defaultCodeType() {
        if (null == _defaultCodeType) {
            _defaultCodeType = get(DEFAULT_CODE_TYPE_IMPL);
        }
        return _defaultCodeType;
    }

    private File _tmpDir = null;

    /**
     * Return {@link RythmConfigurationKey#HOME_TMP} without lookup
     *
     * @return temp dir
     */
    public File tmpDir() {
        if (null == _tmpDir) {
            URI uri = get(HOME_TMP);
            _tmpDir = new File(uri.getPath());
        }
        return _tmpDir;
    }

    private List<URI> _templateHome = null;

    /**
     * Return {@link RythmConfigurationKey#HOME_TEMPLATE} without lookup
     *
     * @return template home
     */
    public List<URI> templateHome() {
        if (null == _templateHome) {
            Object o = get(RythmConfigurationKey.HOME_TEMPLATE);
            if (o instanceof URI) {
                _templateHome = Arrays.asList(new URI[]{(URI)o});
            } else if (o instanceof List) {
                _templateHome = (List<URI>)o;
            } else if (o instanceof File) {
                File root = (File) o;
                _templateHome = Arrays.asList(new URI[]{root.toURI()});
            }
        }
        return _templateHome;
    }

    /**
     * Set template source home path
     * <p/>
     * <p><b>Note</b>, this is not supposed to be used by user application or third party plugin</p>
     */
    public void setTemplateHome(File home) {
        raw.put(HOME_TEMPLATE.getKey(), home);
        data.put(HOME_TEMPLATE, home);
    }

    private IByteCodeEnhancer _byteCodeEnhancer = IByteCodeEnhancer.INSTS.NULL;

    /**
     * Return {@link RythmConfigurationKey#CODEGEN_BYTE_CODE_ENHANCER} without lookup
     *
     * @return the byte code enhancer implementation
     */
    public IByteCodeEnhancer byteCodeEnhancer() {
        if (IByteCodeEnhancer.INSTS.NULL == _byteCodeEnhancer) {
            _byteCodeEnhancer = get(CODEGEN_BYTE_CODE_ENHANCER);
        }
        return _byteCodeEnhancer;
    }

    private ISourceCodeEnhancer _srcEnhancer = null;

    public ISourceCodeEnhancer sourceEnhancer() {
        if (null == _srcEnhancer) {
            _srcEnhancer = get(CODEGEN_SOURCE_CODE_ENHANCER);
        }
        return _srcEnhancer;
    }

    private Locale _locale = null;

    /**
     * Get {@link RythmConfigurationKey#I18N_LOCALE} without lookup
     *
     * @return locale
     */
    public Locale locale() {
        if (null == _locale) {
            _locale = get(I18N_LOCALE);
        }
        return _locale;
    }

    private List<String> _messageSources = null;

    /**
     * Get {@link RythmConfigurationKey#I18N_MESSAGE_SOURCES} without lookup
     */
    public List<String> messageSources() {
        if (null == _messageSources) {
            _messageSources = Arrays.asList(get(I18N_MESSAGE_SOURCES).toString().split("[, \\t]+"));
        }
        return _messageSources;
    }

    private II18nMessageResolver _i18n = null;

    /**
     * Get {@link RythmConfigurationKey#I18N_MESSAGE_RESOLVER} without lookup
     */
    public II18nMessageResolver i18nMessageResolver() {
        if (null == _i18n) {
            _i18n = get(I18N_MESSAGE_RESOLVER);
        }
        return _i18n;
    }

    private String _resourceBundleEncoding = null;

    /**
     * Get {@link RythmConfigurationKey#RESOURCE_BUNDLE_ENCODING} without lookup
     * @return
     */
    public String resourceBundleEncoding() {
        if (null == _resourceBundleEncoding) {
            _resourceBundleEncoding = get(RESOURCE_BUNDLE_ENCODING);
            if (null == _resourceBundleEncoding) {
                _resourceBundleEncoding = "default";
            }
        }
        return _resourceBundleEncoding;
    }

    public static final ResourceBundle.Control DEF_RESOURCE_BUNDLE_CONTROL = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT);
    private ResourceBundle.Control _resourceBundleControl;
    public synchronized ResourceBundle.Control resourceBundleControl() {
        if (null != _resourceBundleControl) {
            return _resourceBundleControl;
        }
        final String encoding = resourceBundleEncoding();
        if ("default".equals(encoding)) {
            _resourceBundleControl = DEF_RESOURCE_BUNDLE_CONTROL;
        } else {
            _resourceBundleControl = new ResourceBundle.Control() {
                @Override
                public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload) throws IllegalAccessException, InstantiationException, IOException {
                    // The below is a copy of the default implementation.
                    String bundleName = toBundleName(baseName, locale);
                    String resourceName = toResourceName(bundleName, "properties");
                    ResourceBundle bundle = null;
                    InputStream stream = null;
                    if (reload) {
                        URL url = loader.getResource(resourceName);
                        if (url != null) {
                            URLConnection connection = url.openConnection();
                            if (connection != null) {
                                connection.setUseCaches(false);
                                stream = connection.getInputStream();
                            }
                        }
                    } else {
                        stream = loader.getResourceAsStream(resourceName);
                    }
                    if (stream != null) {
                        try {
                            // Only this line is changed to make it to read properties files as UTF-8.
                            bundle = new PropertyResourceBundle(new InputStreamReader(stream, encoding));
                        } finally {
                            stream.close();
                        }
                    }
                    return bundle;
                }
            };
        }
        return _resourceBundleControl;
    }

    private String _suffix = null;

    /**
     * Get {@link RythmConfigurationKey#RESOURCE_NAME_SUFFIX} without lookup
     */
    public String resourceNameSuffix() {
        if (null == _suffix) {
            _suffix = get(RESOURCE_NAME_SUFFIX);
        }
        return _suffix;
    }

    private Integer _resourceRefreshInterval = null;

    /**
     * Get {@link RythmConfigurationKey#RESOURCE_REFRESH_INTERVAL}
     * @return the key for the resourceRefreshInterval
     */
    public long resourceRefreshInterval() {
        if (null == _resourceRefreshInterval) {
            _resourceRefreshInterval = get(RESOURCE_REFRESH_INTERVAL);
        }
        return _resourceRefreshInterval.longValue();
    }

    private Boolean _autoScan = null;

    public boolean autoScan() {
        if (null == _autoScan) {
            _autoScan = get(RESOURCE_AUTO_SCAN);
        }
        return _autoScan;
    }

    private String _allowedSysProps = null;

    public String allowedSystemProperties() {
        if (null == _allowedSysProps) {
            _allowedSysProps = get(SANDBOX_ALLOWED_SYSTEM_PROPERTIES);
        }
        return _allowedSysProps;
    }

    private Boolean _sandboxTmpIO = null;

    public boolean sandboxTmpIO() {
        if (null == _sandboxTmpIO) {
            _sandboxTmpIO = get(SANDBOX_TEMP_IO_ENABLED);
        }
        return _sandboxTmpIO;
    }

    private Boolean _hasGlobalInclude = null;

    public boolean hasGlobalInclude() {
        if (engine.insideSandbox()) return false;
        if (null == _hasGlobalInclude) {
            ITemplateResource rsrc = engine.resourceManager().getResource("__global.rythm");
            _hasGlobalInclude = rsrc.isValid();
        }
        return _hasGlobalInclude;
    }

    public static final RythmConfiguration EMPTY_CONF = new RythmConfiguration(Collections.<String, Object>emptyMap(), null);

    /**
     * Return <tt>RythmConfiguration</tt> instance of current RythmEngine, or
     * if it is not inside a RythmEngine runtime context, an {@link #EMPTY_CONF empty configuration}
     * is returned
     *
     * @return the configuration instance associated with engine running in the current thread
     */
    public static RythmConfiguration get() {
        RythmEngine engine = RythmEngine.get();
        return null != engine ? engine.conf() : EMPTY_CONF;
    }

    private final static ILogger logger = Logger.get(RythmConfiguration.class);

    public void debug() {
        logger.info("start to dump rythm configuration >>>");
        for (RythmConfigurationKey k : RythmConfigurationKey.values()) {
            logger.info("%s: %s", k, get(k));
        }
        logger.info("end dumping rythm configuration <<<");
    }

}
