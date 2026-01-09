package net.sanctuary.servers.craftedgateway.config;

public final class ConfigKeys {
    private ConfigKeys() {
    }

    public static final class Votd {
        public static final String BIBLE_VERSION = "votd.bible-version";
        public static final String API_URL = "votd.api-url";
        public static final String RANDOM_API_URL = "votd.random-api-url";
        public static final String MESSAGE_FORMAT = "votd.message-format";
        public static final String DEBUG_LOGGING = "votd.debug-logging";
        public static final String JOIN_ENABLED = "votd.join-enabled";
        public static final String JOIN_FORMAT = "votd.join-format";
        public static final String RANDOM_ANNOUNCEMENT_FORMAT = "votd.random-announcement-format";
        public static final String ANNOUNCEMENT_FORMAT = "votd.announcement-format";
        public static final String ANNOUNCEMENT_INTERVAL_MINUTES = "votd.announcement-interval-minutes";
        public static final String ANNOUNCEMENT_ENABLED = "votd.announcement-enabled";

        private Votd() {
        }
    }

    public static final class Radio {
        public static final String ENABLED = "radio.enabled";
        public static final String DEBUG_LOGGING = "radio.debug-logging";
        public static final String WEBSOCKET_URL = "radio.websocket-url";
        public static final String STATION_URL = "radio.station-url";
        public static final String URL_LABEL = "radio.url-label";
        public static final String STATION_SHORTCODE = "radio.station-shortcode";
        public static final String MESSAGE_FORMAT = "radio.message-format";
        public static final String RECONNECT_DELAY_SECONDS = "radio.reconnect-delay-seconds";
        public static final String ANNOUNCEMENT_ENABLED = "radio.announcement-enabled";

        private Radio() {
        }
    }

    public static final class Tablist {
        public static final String ENABLED = "tablist.enabled";
        public static final String UPDATE_INTERVAL_TICKS = "tablist.update-interval-ticks";
        public static final String TIME_FORMAT = "tablist.time-format";
        public static final String HEADER = "tablist.header";
        public static final String FOOTER = "tablist.footer";
        public static final String DEBUG_LOGGING = "tablist.debug-logging";

        private Tablist() {
        }
    }

    public static final class Metrics {
        public static final String ENABLED = "metrics.enabled";
        public static final String LOG_INTERVAL_MINUTES = "metrics.log-interval-minutes";

        private Metrics() {
        }
    }
}
