package net.sanctuary.servers.craftedgateway.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Objects;

public final class MessageTemplate {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageTemplate() {
    }

    public static Component render(String template, String... keyValues) {
        return render(template, (Object[]) keyValues);
    }

    public static Component render(String template, Object... keyValues) {
        if (template == null) {
            return Component.empty();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Key/value pairs must be even.");
        }
        validateKeys(keyValues);
        if (usesLegacyFormat(template)) {
            return LEGACY_SERIALIZER.deserialize(applyLegacyPlaceholders(template, keyValues));
        }

        TagResolver.Builder resolver = TagResolver.builder();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = keyAt(keyValues, i);
            if (key == null) {
                continue;
            }
            Object value = keyValues[i + 1];
            if (value instanceof Component) {
                resolver.resolver(Placeholder.component(key, (Component) value));
            } else {
                resolver.resolver(Placeholder.unparsed(key, Objects.toString(value, "")));
            }
        }
        String miniTemplate = applyMiniMessagePlaceholders(template, keyValues);
        return MINI_MESSAGE.deserialize(miniTemplate, resolver.build());
    }

    public static boolean usesLegacyFormat(String template) {
        if (template == null) {
            return false;
        }
        return template.indexOf('&') >= 0 && template.indexOf('<') < 0;
    }

    private static String applyMiniMessagePlaceholders(String template, Object... keyValues) {
        if (keyValues.length == 0 || template.indexOf('{') < 0) {
            return template;
        }
        return replacePlaceholders(template, (output, key) -> {
            if (!hasKey(keyValues, key)) {
                return false;
            }
            output.append('<').append(key).append('>');
            return true;
        });
    }

    private static String applyLegacyPlaceholders(String template, Object... keyValues) {
        if (keyValues.length == 0 || template.indexOf('{') < 0) {
            return template;
        }
        return replacePlaceholders(template, (output, key) -> {
            String value = findValue(keyValues, key);
            if (value == null) {
                return false;
            }
            output.append(value);
            return true;
        });
    }

    private interface PlaceholderAppender {
        boolean appendReplacement(StringBuilder output, String key);
    }

    private static String replacePlaceholders(String template, PlaceholderAppender appender) {
        if (template == null) {
            return null;
        }
        int open = template.indexOf('{');
        if (open < 0) {
            return template;
        }
        StringBuilder result = new StringBuilder(template.length());
        int index = 0;
        int length = template.length();
        while (index < length) {
            open = template.indexOf('{', index);
            if (open < 0) {
                result.append(template, index, length);
                break;
            }
            int close = template.indexOf('}', open + 1);
            if (close < 0) {
                result.append(template, index, length);
                break;
            }
            result.append(template, index, open);
            String key = template.substring(open + 1, close);
            if (!appender.appendReplacement(result, key)) {
                result.append(template, open, close + 1);
            }
            index = close + 1;
        }
        return result.toString();
    }

    private static boolean hasKey(Object[] keyValues, String key) {
        return findKeyIndex(keyValues, key) >= 0;
    }

    private static String findValue(Object[] keyValues, String key) {
        int index = findKeyIndex(keyValues, key);
        if (index < 0) {
            return null;
        }
        Object value = keyValues[index + 1];
        if (value instanceof Component) {
            throw new IllegalArgumentException(
                "Component values are not supported in legacy templates for key '" + key + "'."
            );
        }
        return Objects.toString(value, "");
    }

    private static int findKeyIndex(Object[] keyValues, String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 0; i < keyValues.length; i += 2) {
            String candidate = keyAt(keyValues, i);
            if (candidate != null && key.equals(candidate)) {
                return i;
            }
        }
        return -1;
    }

    private static String keyAt(Object[] keyValues, int index) {
        Object value = keyValues[index];
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        throw new IllegalArgumentException("Placeholder key at index " + index + " must be a String.");
    }

    private static void validateKeys(Object[] keyValues) {
        for (int i = 0; i < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (key != null && !(key instanceof String)) {
                throw new IllegalArgumentException(
                    "Placeholder key at index " + i + " must be a String."
                );
            }
        }
    }
}
