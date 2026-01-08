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
        if (template == null) {
            return Component.empty();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Key/value pairs must be even.");
        }
        if (usesLegacyFormat(template)) {
            return LEGACY_SERIALIZER.deserialize(applyLegacyPlaceholders(template, keyValues));
        }

        String miniTemplate = template;
        TagResolver.Builder resolver = TagResolver.builder();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = keyValues[i];
            String value = Objects.toString(keyValues[i + 1], "");
            miniTemplate = miniTemplate.replace("{" + key + "}", "<" + key + ">");
            resolver.resolver(Placeholder.unparsed(key, value));
        }
        return MINI_MESSAGE.deserialize(miniTemplate, resolver.build());
    }

    private static boolean usesLegacyFormat(String template) {
        return template.indexOf('&') >= 0 && template.indexOf('<') < 0;
    }

    private static String applyLegacyPlaceholders(String template, String... keyValues) {
        String result = template;
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = keyValues[i];
            String value = Objects.toString(keyValues[i + 1], "");
            result = result.replace("{" + key + "}", value);
        }
        return result;
    }
}
