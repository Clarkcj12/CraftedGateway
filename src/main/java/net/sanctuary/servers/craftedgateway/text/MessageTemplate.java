package net.sanctuary.servers.craftedgateway.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class MessageTemplate {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageTemplate() {
    }

    public static Component render(String template, String... keyValues) {
        if (template == null) {
            return Component.empty();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Key/value pairs must be even.");
        }
        if (usesLegacyFormat(template)) {
            return LEGACY_SERIALIZER.deserialize(applyLegacyPlaceholders(template, keyValues));
        }

        TagResolver.Builder resolver = TagResolver.builder();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = keyValues[i];
            if (key == null) {
                continue;
            }
            String value = keyValues[i + 1];
            if (value == null) {
                value = "";
            }
            resolver.resolver(Placeholder.unparsed(key, value));
        }
        String miniTemplate = applyMiniMessagePlaceholders(template, keyValues);
        return MINI_MESSAGE.deserialize(miniTemplate, resolver.build());
    }

    private static boolean usesLegacyFormat(String template) {
        return template.indexOf('&') >= 0 && template.indexOf('<') < 0;
    }

    private static String applyMiniMessagePlaceholders(String template, String... keyValues) {
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

    private static String applyLegacyPlaceholders(String template, String... keyValues) {
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

    private static boolean hasKey(String[] keyValues, String key) {
        return findKeyIndex(keyValues, key) >= 0;
    }

    private static String findValue(String[] keyValues, String key) {
        int index = findKeyIndex(keyValues, key);
        if (index < 0) {
            return null;
        }
        String value = keyValues[index + 1];
        return value == null ? "" : value;
    }

    private static int findKeyIndex(String[] keyValues, String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 0; i < keyValues.length; i += 2) {
            String candidate = keyValues[i];
            if (key.equals(candidate)) {
                return i;
            }
        }
        return -1;
    }
}
