package com.shanmei.tools.testing;

import java.io.IOException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

final class GooglePlayConnection {
    static final long CHECK_TIMEOUT_MS = 4_000;
    private static final int SOCKET_TIMEOUT_MS = 2_500;
    private static final String STORE_URL = "https://play.google.com/store";

    static boolean isReachable() {
        try {
            return isReachable((HttpsURLConnection) new URL(STORE_URL).openConnection());
        } catch (IOException e) {
            return false;
        }
    }

    static boolean isReachable(HttpsURLConnection connection) {
        try {
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(SOCKET_TIMEOUT_MS);
            connection.setReadTimeout(SOCKET_TIMEOUT_MS);
            int status = connection.getResponseCode();
            return status >= 200 && status < 400;
        } catch (IOException e) {
            return false;
        } finally {
            connection.disconnect();
        }
    }

    private GooglePlayConnection() {}
}
