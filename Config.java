package me.asdev.util.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import me.asdev.util.collections.ListUtil;

/**
 * A wrapper for the built-in YamlConfiguration, allowing for quick creation,
 * saving and reloading. 
 * This class also includes a series of utility features to make certain tasks easier.
 * For example, if you want to remove duplicate items from a list of strings, you'd use
 * {@link #makeStringListDistinct(String)}
 * <br><br>
 * This class can also work for language files, and has the option to include the prefix, to which you can set the location with {@link #setPrefixPath(String)} (for example, "prefix.prefix").
 * <br><br>
 * Example YAML:
 * <pre><code>
	prefix:
	   prefix: "&6Example &8> &r"
	messages:
	    test: "My test message!" 
 * </pre>
 * Unless {@link #excludePrefix} is true, The message would become "&6Example &7> &rMy test message!"<br>
 * 
 * @author ASDev
 * @version 1.1.9
 *
 */
public class Config extends YamlConfiguration {
	
	public static boolean THROW_EXCEPTION_ON_UNSUPPORTED_OPERATION = true;

	private File configFile;
	private Reader reader;
	private Plugin plugin;
	private Instant lastReload = null;
	
	/**
	 * Was this config loaded using <b>new Config(File, true)</b>
	 */
	private boolean immediatelyLoaded = false;
	private boolean saveSupported = true;

	/**
	 * Where in the config is the messages prefix located?<br>
	 * Example: <br>
	 * <pre>    "prefix.prefix"</pre> would be the following in YAML:
	 * <pre>
	 * prefix:
	 *     prefix: "&6Example &8> &r"
	 * </pre>
	 * This prefix will only be used if {@link #excludePrefix} is false.
	 */
	private String prefixPath = "prefix.prefix";
	
	/**
	 * Should the prefix located at {@link #prefixPath } be omitted from messages?
	 */
	private boolean excludePrefix = false;

	public Config(File file) {
		this(file, false);
	}


	public Config(File file, boolean loadNow) {
		this.configFile = file;
		this.immediatelyLoaded = loadNow;

		if (loadNow) {
			try {
				load(file);
			} catch (IOException | InvalidConfigurationException ex) {
				ex.printStackTrace();
				throw new RuntimeException(ex);
			}
		}
	}

	public Config(InputStream inputStream) {
		this(new InputStreamReader(inputStream));
	}

	public Config(Reader reader) {
		this.reader = reader;
		try {
			load(reader);
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}
	}
	
	public static Config defaultConfig(Plugin plugin) {
		return defaultConfig(plugin, "config.yml");
	}
	
	public static Config defaultLang(Plugin plugin) {
		return defaultConfig(plugin, "lang.yml");
	}
	
	public static Config defaultConfig(Plugin plugin, String name) {
		plugin.getDataFolder().mkdir();
		return new Config(new File(plugin.getDataFolder(), name)).setPlugin(plugin).setDefaults2(new Config(plugin.getResource(name))).saveResource().reload();
	}
	
	public static Config wrapper(FileConfiguration config) {
		Config newConfig =  new Config(new File("none.yml")).setDefaults2(config);
		newConfig.configFile = null;
		newConfig.saveSupported = false;
		
		return newConfig;
	}
	
	public Config setDefaults2(Configuration defaults) {
		setDefaults(defaults);
		return this;
	}

	public File getConfigFile() {
		return configFile;
	}

	public Config save() {
		if (configFile == null || !saveSupported) {
			if (THROW_EXCEPTION_ON_UNSUPPORTED_OPERATION) {
				throw new UnsupportedOperationException("Saving is not supported on this config.");
			}
			return this;
		}

		try {
			save(this.configFile);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		
		return this;
	}

	public Config reload() {
		if (!saveSupported) {
			if (THROW_EXCEPTION_ON_UNSUPPORTED_OPERATION) {
				throw new UnsupportedOperationException("Reloading is not supported on this config.");
			}
			return this;
		}
		
		lastReload = Instant.now();
		try {
			if (configFile == null) {
				load(reader);
			} else {
				load(configFile);
				if (plugin != null && plugin.getResource(configFile.getName()) != null) {
					setDefaults(new Config(plugin.getResource(configFile.getName())));
				}
			}
			
		} catch (IOException | InvalidConfigurationException | NullPointerException ex) {
			ex.printStackTrace();
		}

		return this;
	}
	
	public Instant getLastReload() {
		return lastReload;
	}
	
	public boolean isImmediatelyLoaded() {
		return immediatelyLoaded;
	}
	
	public boolean isSaveSupported() {
		return saveSupported;
	}

	public Plugin getPlugin() {
		return plugin;
	}

	public Config setPlugin(Plugin plugin) {
		this.plugin = plugin;
		plugin.getDataFolder().mkdir();
		return this;
	}

	/**
	 * Copies this configuration from the plugin jar. Requires that a plugin be set
	 * with {@link Config#setPlugin(Plugin) }
	 * 
	 * @return this config.
	 */
	public Config createIfNonexistant() {
		if (!saveSupported) {
			if (THROW_EXCEPTION_ON_UNSUPPORTED_OPERATION) {
				throw new UnsupportedOperationException("Saving is not supported on this config.");
			}
			return this;
		}
		
		if (configFile != null && !configFile.exists()) {
			saveResource();
		}

		return this;
	}

	public Config saveResource() {
		if (!saveSupported) {
			if (THROW_EXCEPTION_ON_UNSUPPORTED_OPERATION) {
				throw new UnsupportedOperationException("Saving is not supported on this config.");
			}
			return this;
		}
		
		saveResource(false);
		return this;
	}

	public Config saveResource(boolean replace) {
		if (plugin == null || configFile == null || !saveSupported) {
			if (THROW_EXCEPTION_ON_UNSUPPORTED_OPERATION) {
				throw new UnsupportedOperationException("Saving is not supported on this config.");
			}
			return this;
		}

		plugin.saveResource(configFile.getName(), replace);
		return this;
	}

	public String getMessage(String key) {
		return getMessage(key, new Object[0]);
	}

	public String getMessage(String key, Object... replacements) {
		return color(getMessageRaw(key, excludePrefix, replacements));
	}

	public String getMessageRaw(String key) {
		return getMessageRaw(key, new Object[0]);
	}

	public String getMessageRaw(String key, Object... replacements) {
		String val = getString(key, String.format("No entry for %s", key));
		if (!excludePrefix && !key.equalsIgnoreCase(prefixPath)) {
			val = getString(prefixPath) + val;
		}

		for (int i = 0; i < replacements.length; i++) {
			if (val.contains(String.format("{%d}", i))) {
				if (replacements[i] == null) {
					val = val.replace(String.format("{%d}", i), "null");
					continue;
				}
				val = val.replace(String.format("{%d}", i), String.valueOf(replacements[i]));
			}
		}

		return val;
	}

	public List<String> getMessageList(String key) {
		return getMessageList(key, new Object[0]);
	}
	
	public List<String> getMessageList(String key, Object... replacements) {
		List<String> messagesRaw = getStringList(key);
		List<String> colored = new ArrayList<>();
		
		messagesRaw.forEach(message -> {
			String formattedMessage = message;
			
			if (replacements.length > 0) {
				for (int i = 0; i < replacements.length; i++) {
					if (formattedMessage.contains(String.format("{%d}", i))) {
						formattedMessage = formattedMessage.replace(String.format("{%d}", i), String.valueOf(replacements[i]));
					}
				}
			}
			
			colored.add(color(formattedMessage));
		});
		
		return colored;
	}

	public static String color(String message) {
		return color('&', message);
	}

	public static String color(char c, String message) {
		return ChatColor.translateAlternateColorCodes(c, message);
	}

	/**
	 * Example usage: <b>getPrefixedMessageAdvanced("my.message", "playerName",
	 * "JohnDoe", "age", 30);</b><br>
	 * Replaces <b>%playerName</b> with <b>JohnDoe</b> and <b>%age</b> with
	 * <b>30</b>
	 * <br><br>
	 * <b>NOTE:</b> This method will return the result of {@link #getMessageAdvanced(String, Object...)} if <i>{@link #excludePrefix}</i> is <i>true</i>
	 * 
	 */
	public String getPrefixedMessageAdvanced(String key, Object... replacements) {
		if (excludePrefix) {
			return getMessageAdvanced(key, replacements);
		}
		
		return String.format("%s" + getMessageAdvanced(key, replacements), color(getString(prefixPath)));
	}

	/**
	 * Example usage: <b>getPrefixedMessageAdvanced("my.message", "playerName",
	 * "JohnDoe", "age", 30);</b><br>
	 * &nbsp;&nbsp;Replaces <b>%playerName</b> with <b>JohnDoe</b> and <b>%age</b> with
	 * <b>30</b>
	 */
	public String getMessageAdvanced(String key, Object... replacements) {
		String message = getString(key, "Message \"%s\" does not exist.");

		for (int i = 0; i < replacements.length; i += 2) {
			if (message.contains("%" + replacements[i])) {
				message = message.replace("%" + replacements[i],
						String.valueOf(replacements.length <= i + 1 ? "null" : replacements[i + 1]));
			}
		}

		return color(message);
	}

	/**
	* @return Where in the config the message prefix is located.
	*/
	public String getPrefixPath() {
		return prefixPath;
	}

	public Config setPrefixPath(String prefixPath) {
		this.prefixPath = prefixPath;
		
		return this;
	}
	
	public boolean isExcludePrefix() {
		return excludePrefix;
	}
	
	public Config setExcludePrefix(boolean excludePrefix) {
		this.excludePrefix = excludePrefix;
		return this;
	}
	
	public Config autoExcludePrefix() {
		return setExcludePrefix(!getBoolean("prefix.enabled", true));
	}

	public Config addToStringList(String key, String... values) {
		addToStringList(key, Arrays.asList(values));
		return this;
	}

	public Config addToStringList(String key, Collection<String> values) {
		List<String> current = getStringList(key);

		for (String string : values) {
			current.add(string);
		}

		set(key, current);
		return this;
	}

	public Config removeFromStringList(String key, String... values) {
		removeFromStringList(key, Arrays.asList(values));
		return this;
	}

	public Config removeFromStringList(String key, Collection<String> values) {
		List<String> current = getStringList(key);

		for (String string : values) {
			if (current.contains(string)) {
				current.remove(string);
			}
		}

		set(key, current);
		return this;
	}

	public List<String> getDistinctStringList(String key) {
		return getStringList(key).stream().distinct().collect(Collectors.toList());
	}

	/**
	 * Removes duplicate items from the list specified at <b>key</b> and writes the changes to the config.
	 * @param key The list in question, for example "player-names" or "ranks.admin.players"
	 * @return The same instance of this class.
	 */
	public Config makeStringListDistinct(String key) {
		set(key, getDistinctStringList(key));
		return this;
	}
	

	public Config addToIntegerList(String key, int... vals) {
		List<Integer> intList = getIntegerList(key);
		
		for (int val : intList) {
			intList.add(val);
		}
		
		set(key, intList);
		return this;
	}
	
	public Config removeFromIntegerList(String key, int... vals) {
		List<Integer> intList = getIntegerList(key);
		
		for (int val : intList) {
			if (intList.contains(val)) {
				intList.remove((Integer) val); // I cast to Integer here because using int would remove it from the specified position, not the value. 
											   //	Not casting this would likely cause an IndexOutOfBoundsException.
			}
		}
		
		set(key, intList);
		return this;
	}
	
	public Config addToDoubleList(String key, double... vals) {
		List<Double> list = getDoubleList(key);
		
		for (double val : vals) {
			list.add(val);
		}
		
		set(key, list);
		return this;
	}
	
	public Config removeFromDoubleList(String key, double... vals) {
		List<Double> list = getDoubleList(key);
		
		for (double val : list) {
			if (list.contains(val)) {
				list.remove(val);
			}
		}
		
		set(key, list);
		return this;
	}
	
	
	/**
	 * Removes duplicate items from the list specified at <b>key</b> and writes the changes to the config.
	 * @param key The list in question, for example "player-money" or "leaderboard.player-scores"
	 * @return The same instance of this class.
	 */
	public Config makeListDistinct(String key) {
		List<?> list = getList(key);
		if (list == null) {
			return this;
		}
		List<?> distinct = ListUtil.distinct(list);
		
		set(key, distinct);
		return this;
	}
	

	
}
