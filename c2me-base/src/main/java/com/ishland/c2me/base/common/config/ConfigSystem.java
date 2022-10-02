package com.ishland.c2me.base.common.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.ishland.c2me.base.common.util.BooleanUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Supplier;

public class ConfigSystem {

    public static final Logger LOGGER = LoggerFactory.getLogger("C2ME Config System");

    private static final long CURRENT_CONFIG_VERSION = 3;

    private static final Supplier<CommentedFileConfig> configSupplier =
            () -> CommentedFileConfig.builder(FabricLoader.getInstance().getConfigDir().resolve("c2me.toml"))
                    .preserveInsertionOrder()
                    .sync()
                    .build();

    private static final CommentedFileConfig CONFIG;

    private static final HashSet<String> visitedConfig = new HashSet<>();

    static {
        CommentedFileConfig config = configSupplier.get();
        try {
            config.load();
        } catch (Throwable t) {
            config = configSupplier.get();
            config.save();
        }

        Updaters.update(config);
        if (config.getInt("version") != CURRENT_CONFIG_VERSION) {
            config.clear();
            LOGGER.warn("Config version mismatch, resetting config");
            config.set("version", CURRENT_CONFIG_VERSION);
        }

        visitedConfig.add("version");

        CONFIG = config;
    }

    public static void flushConfig() {
        purgeUnusedRecursively("", CONFIG);
        CONFIG.save();
    }

    private static void purgeUnusedRecursively(String prefix, CommentedConfig config) {
        for (Iterator<? extends CommentedConfig.Entry> iterator = config.entrySet().iterator(); iterator.hasNext(); ) {
            CommentedConfig.Entry entry = iterator.next();
            final String key = prefix + "." + entry.getKey();
            if (entry.getValue() instanceof CommentedConfig child) {
                purgeUnusedRecursively(key, child);
                if (child.isEmpty()) {
                    LOGGER.info("Removing config entry {} because it is not used", key);
                    iterator.remove();
                }
            } else if (!visitedConfig.contains(key.substring(".".length()))) {
                LOGGER.info("Removing config entry {} because it is not used", key);
                iterator.remove();
            }
        }
    }

    public static class ConfigAccessor {

        private static final String propertyPrefix = "c2me.base.config.override.";

        private final StringBuilder incompatibilityReason = new StringBuilder();

        private String key;
        private String comment;
        private boolean incompatibilityDetected;

        public ConfigAccessor key(String key) {
            this.key = key;
            markVisited();
            return this;
        }

        public ConfigAccessor comment(String comment) {
            this.comment = comment;
            return this;
        }

        public ConfigAccessor incompatibleMod(String modId, String predicate) {
            try {
                final Optional<ModContainer> optional = FabricLoader.getInstance().getModContainer(modId);
                if (optional.isPresent()) {
                    final ModContainer modContainer = optional.get();
                    final Version version = modContainer.getMetadata().getVersion();

                    final VersionPredicate versionPredicate = VersionPredicate.parse(predicate);
                    if (versionPredicate.test(version)) {
                        final String reason = String.format("Incompatible with %s@%s (%s) (defined in c2me)", modId, version.getFriendlyString(), predicate);
                        disableConfigWithReason(reason);
                    }
                }
            } catch (Throwable t) {
                throw new IllegalArgumentException(t);
            }
            return this;
        }

        public long getLong(long def, long incompatibleDef, LongChecks... checks) {
            findModDefinedIncompatibility();
            final String systemPropertyOverride = getSystemPropertyOverride();
            final Object configured = systemPropertyOverride != null ? systemPropertyOverride : CONFIG.get(this.key);
            boolean isDefaultValue = false;
            if (configured != null) {
                if (String.valueOf(configured).equals("default")) { // default placeholder
                    isDefaultValue = true;
                } else if (!(configured instanceof Number)) { // try to fix config
                    try {
                        CONFIG.set(this.key, Long.valueOf(String.valueOf(configured)));
                    } catch (NumberFormatException e) {
                        LOGGER.warn("Invalid configured value: {} -> {}", this.key, configured);
                        CONFIG.remove(this.key);
                    }
                }
            } else {
                isDefaultValue = true;
            }
            generateDefaultEntry(def, incompatibleDef);

            if (this.incompatibilityDetected) return incompatibleDef;
            long configLong = isDefaultValue ? def : CONFIG.getLong(this.key);
            if (checkConfig(configLong, checks)) {
                return configLong;
            } else {
                CONFIG.remove(this.key);
                generateDefaultEntry(def, incompatibleDef);
                return def;
            }
        }

        private boolean checkConfig(long value, LongChecks... checks) {
            for (LongChecks check : checks) {
                if (!check.test(value)) return false;
            }
            return true;
        }

        public boolean getBoolean(boolean def, boolean incompatibleDef) {
            findModDefinedIncompatibility();
            final String systemPropertyOverride = getSystemPropertyOverride();
            final Object configured = systemPropertyOverride != null ? systemPropertyOverride : CONFIG.get(this.key);
            boolean isDefaultValue = false;
            if (configured != null) {
                if (String.valueOf(configured).equals("default")) { // default placeholder
                    isDefaultValue = true;
                } else if (!(configured instanceof Boolean)) { // try to fix config
                    try {
                        CONFIG.set(this.key, BooleanUtils.parseBoolean(String.valueOf(configured)));
                    } catch (BooleanUtils.BooleanFormatException e) {
                        LOGGER.warn("Invalid configured value: {} -> {}", this.key, configured);
                        CONFIG.remove(this.key);
                    }
                }
            } else {
                isDefaultValue = true;
            }
            generateDefaultEntry(def, incompatibleDef);

            return this.incompatibilityDetected ? incompatibleDef : (isDefaultValue ? def : CONFIG.get(this.key));
        }

        public <T extends Enum<T>> T getEnum(Class<T> enumClass, T def, T incompatibleDef) {
            findModDefinedIncompatibility();
            final String systemPropertyOverride = getSystemPropertyOverride();
            final Object configured = systemPropertyOverride != null ? systemPropertyOverride : CONFIG.get(this.key);
            boolean isDefaultValue = false;
            if (configured != null) {
                if (String.valueOf(configured).equals("default")) {
                    isDefaultValue = true;
                } else {
                    try {
                        CONFIG.set(this.key, Enum.valueOf(enumClass, String.valueOf(configured)));
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Invalid configured value: {} -> {}", this.key, configured);
                        CONFIG.remove(this.key);
                    }
                }
            } else {
                isDefaultValue = true;
            }
            generateDefaultEntry(def, incompatibleDef);

            return this.incompatibilityDetected ? incompatibleDef : (isDefaultValue ? def : CONFIG.getEnum(this.key, enumClass));
        }

        public String getString(String def, String incompatibleDef) {
            findModDefinedIncompatibility();
            final String systemPropertyOverride = getSystemPropertyOverride();
            final Object configured = systemPropertyOverride != null ? systemPropertyOverride : CONFIG.get(this.key);
            boolean isDefaultValue = false;
            if (configured != null) {
                if (String.valueOf(configured).equals("default")) { // default placeholder
                    isDefaultValue = true;
                } else if (!(configured instanceof String)) { // try to fix config
                    CONFIG.set(this.key, String.valueOf(configured));
                }
            } else {
                isDefaultValue = true;
            }
            generateDefaultEntry(def, incompatibleDef);

            return this.incompatibilityDetected ? incompatibleDef : (isDefaultValue ? def : CONFIG.get(this.key));
        }

        private void findModDefinedIncompatibility() {
            for (ModContainer modContainer : FabricLoader.getInstance().getAllMods()) {
                final CustomValue incompatibilitiesValue = modContainer.getMetadata().getCustomValue("c2me:incompatibleConfig");
                if (incompatibilitiesValue != null && incompatibilitiesValue.getType() == CustomValue.CvType.ARRAY) {
                    final CustomValue.CvArray incompatibilities = incompatibilitiesValue.getAsArray();
                    for (CustomValue value : incompatibilities) {
                        if (value.getType() == CustomValue.CvType.STRING && value.getAsString().equals(this.key)) {
                            final String reason = String.format("Incompatible with %s@%s (defined in %s)",
                                    modContainer.getMetadata().getId(), modContainer.getMetadata().getVersion().getFriendlyString(), modContainer.getMetadata().getId());
                            disableConfigWithReason(reason);
                        }
                    }
                }
            }

        }

        private String getSystemPropertyOverride() {
            final String property = System.getProperty(propertyPrefix + this.key);
            if (property != null) LOGGER.info("Setting {} to {} (defined in system property)", this.key, property);
            return property;
        }

        private void disableConfigWithReason(String reason) {
            incompatibilityReason.append("\n ").append(reason);
            LOGGER.info("Disabling config {}: {}", this.key, reason);
            this.incompatibilityDetected = true;
        }

        private void generateDefaultEntry(Object def, Object incompatibleDef) {
            if (!CONFIG.contains(this.key)) {
                CONFIG.set(this.key, "default");
            }
            final String comment = String.format(" (Default: %s) %s", def, this.comment.replace("\n", "\n "));
            if (this.incompatibilityDetected) {
                CONFIG.setComment(this.key, String.format("%s\n Set to %s for the following reasons: %s ", comment, incompatibleDef, this.incompatibilityReason));
            } else {
                CONFIG.setComment(this.key, comment);
            }
        }

        private void markVisited() {
            visitedConfig.add(this.key);
        }
    }

    public enum LongChecks {
        THREAD_COUNT() {
            @Override
            public boolean test(long value) {
                return value >= 1 && value <= 0x7fff;
            }
        },
        NO_TICK_VIEW_DISTANCE() {
            @Override
            public boolean test(long value) {
                return value >= 2 && value <= 248;
            }
        },
        POSITIVE_VALUES_ONLY() {
            @Override
            public boolean test(long value) {
                return value >= 1;
            }
        };

        public abstract boolean test(long value);

    }

}
