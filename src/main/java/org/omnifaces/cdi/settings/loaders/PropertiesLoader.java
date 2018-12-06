package org.omnifaces.cdi.settings.loaders;

import java.util.Map;

/**
 * Load properties from a resource.
 *
 * Can be self configuring from the settings passed.
 *
 * @author Mark Meany
 */
public interface PropertiesLoader {

	/**
	 * Determines the order that these are used to read properties into the application, lower the priority the earlier the properties are read.
	 *
	 * Implication is that lower priority properties can be overwritten by same values from later loaders.
	 *
	 * @return priority of this loader.
	 */
	Integer priority();

	/**
	 * Load properties from resource, adding them to the settings passed in.
	 *
	 * @param settings
	 *            applications settings
	 */
	void load(Map<? super String, ? super String> settings);
}
