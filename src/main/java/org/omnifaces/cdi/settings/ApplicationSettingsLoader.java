package org.omnifaces.cdi.settings;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.regex.Pattern.quote;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.omnifaces.utils.Lang.isEmpty;
import static org.omnifaces.utils.properties.PropertiesUtils.getStage;
import static org.omnifaces.utils.properties.PropertiesUtils.loadPropertiesFromClasspath;
import static org.omnifaces.utils.properties.PropertiesUtils.loadXMLPropertiesStagedFromClassPath;

import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import javax.inject.Named;

import org.omnifaces.cdi.settings.loaders.PropertiesLoader;

@ApplicationScoped
public class ApplicationSettingsLoader {

	@Inject
	private PropertiesFileLoader propertiesFileLoader;

	@Inject
	@org.omnifaces.cdi.settings.loaders.ApplicationSettingsLoader
	private Instance<PropertiesLoader> loaders;

	private Map<String, String> settings;

	@PostConstruct
	public void init() {

		final Map<String, String> internalSettings = loadPropertiesFromClasspath("META-INF/omni-settings");

		final Map<String, String> mutableSettings = new HashMap<>();

		String stageSystemPropertyName = internalSettings.getOrDefault("stageSystemPropertyName", "omni.stage");
		String settingsSystemPropertyName = internalSettings.getOrDefault("settingsSystemPropertyName", "omni.settings");

		String defaultStage = internalSettings.get("defaultStage");

		loadStageSettingsFromClasspath(internalSettings, mutableSettings, stageSystemPropertyName, defaultStage);

		// Load properties using the default
		loadExternalPropertiesUsingPropertiesFileLoader(mutableSettings, settingsSystemPropertyName);

		loadPropertiesUsingApplicationDefinedLoaders(mutableSettings);

		// Non-overridable special setting
		mutableSettings.put("actualStageName", getStage(stageSystemPropertyName, defaultStage));

		settings = Collections.unmodifiableMap(mutableSettings);
	}

	/**
	 * Load Stage specific properties files from application bundle.
	 *
	 * @param internalSettings
	 *            properties loaded from omni-settings.xml
	 * @param mutableSettings
	 *            container for application settings that loaded properties should be added to
	 * @param stageSystemPropertyName
	 *            name of current stage
	 * @param defaultStage
	 *            default stage, in case nothing for current stage
	 */
	private void loadStageSettingsFromClasspath(final Map<String, String> internalSettings, final Map<String, String> mutableSettings,
			final String stageSystemPropertyName, final String defaultStage) {
		mutableSettings.putAll(loadXMLPropertiesStagedFromClassPath(internalSettings.getOrDefault("fileName", "application-settings.xml"),
				stageSystemPropertyName, defaultStage));
	}

	/**
	 * Load properties from a file, the name of the file coming from a System Property. No System Property, nothing to do.
	 *
	 * Uses custom file loader, but only the one is provided.
	 *
	 * @param mutableSettings
	 *            container for application settings that loaded properties should be added to
	 * @param settingsSystemPropertyName
	 *            name of the system property that should contain the fully qualified name of the file to load
	 */
	private void loadExternalPropertiesUsingPropertiesFileLoader(final Map<String, String> mutableSettings, final String settingsSystemPropertyName) {
		String settingFile = System.getProperty(settingsSystemPropertyName);
		if (settingFile != null) {
			URL url;
			try {
				url = Paths.get(settingFile).toUri().toURL();
			} catch (MalformedURLException e) {
				throw new IllegalStateException("Error loading settings from " + settingFile, e);
			}
			propertiesFileLoader.load(url, mutableSettings);
		}
	}

	/**
	 * Load properties using any implementations in the application that have been injected in.
	 *
	 * Implementations must implement the PropertiesLoader interface and be annotated using the @ApplicationSettingsLoader annotation.
	 *
	 * @param mutableSettings
	 *            container for application settings that loaded properties should be added to
	 */
	private void loadPropertiesUsingApplicationDefinedLoaders(final Map<String, String> mutableSettings) {
		final List<PropertiesLoader> sorted = new ArrayList<>();

		for (PropertiesLoader loader : loaders) {
			sorted.add(loader);
		}

		sorted.sort((x, y) -> x.priority().compareTo(y.priority()));

		for (PropertiesLoader loader : sorted) {
			loader.load(mutableSettings);
		}
	}

	@Produces
	@Named("applicationSettings")
	@ApplicationSettings
	public Map<String, String> getSettings(InjectionPoint injectionPoint) {
		if (injectionPoint == null) {
			return settings;
		}

		ApplicationSettings as = getApplicationSettings(injectionPoint);
		if (as == null) {
			return settings;
		}

		String prefix = as.prefixedBy();

		if (prefix.isEmpty()) {
			return settings;
		}

		return settings.entrySet().stream().filter(e -> e.getKey().startsWith(prefix))
				.collect(toMap(Entry<String, String>::getKey, Entry<String, String>::getValue));
	}

	@Produces
	@ApplicationSetting
	public String getStringSetting(InjectionPoint injectionPoint) {
		String value = settings.get(injectionPoint.getMember().getName());

		if (value == null) {
			ApplicationSetting as = getApplicationSetting(injectionPoint);
			if (as != null) {
				value = as.defaultValue();
			}
		}

		return value;
	}

	@Produces
	@ApplicationSetting
	public Long getLongSetting(InjectionPoint injectionPoint) {
		return Long.valueOf(getStringSetting(injectionPoint));
	}

	@Produces
	@ApplicationSetting
	public Integer getIntegerSetting(InjectionPoint injectionPoint) {
		return Integer.valueOf(getStringSetting(injectionPoint));
	}

	@Produces
	@ApplicationSetting
	public Boolean getBooleanSetting(InjectionPoint injectionPoint) {
		return Boolean.valueOf(getStringSetting(injectionPoint));
	}

	@Produces
	@ApplicationSetting
	public List<String> getSeparatedStringSetting(InjectionPoint injectionPoint) {
		String setting = getStringSetting(injectionPoint);

		if (isEmpty(setting)) {
			return emptyList();
		}

		ApplicationSetting as = getApplicationSetting(injectionPoint);
		String separator = as == null ? "," : as.separatedBy();
		return unmodifiableList(asList(setting.split("\\s*" + quote(separator) + "\\s*")));
	}

	@Produces
	@ApplicationSetting
	public List<Long> getSeparatedLongSetting(InjectionPoint injectionPoint) {
		return unmodifiableList(getSeparatedStringSetting(injectionPoint).stream().map(Long::valueOf).collect(toList()));
	}

	private static ApplicationSetting getApplicationSetting(InjectionPoint injectionPoint) {
		return getQualifier(injectionPoint, ApplicationSetting.class);
	}

	private static ApplicationSettings getApplicationSettings(InjectionPoint injectionPoint) {
		return getQualifier(injectionPoint, ApplicationSettings.class);
	}

	@SuppressWarnings("unchecked")
	private static <A extends Annotation> A getQualifier(InjectionPoint injectionPoint, Class<A> qualifier) {
		for (Annotation annotation : injectionPoint.getQualifiers()) {
			if (qualifier.isInstance(annotation)) {
				return (A) annotation;
			}
		}

		return null;
	}
}