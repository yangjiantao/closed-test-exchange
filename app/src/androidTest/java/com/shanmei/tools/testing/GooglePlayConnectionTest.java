package com.shanmei.tools.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.cert.Certificate;

import javax.net.ssl.HttpsURLConnection;

import org.junit.Test;

public class GooglePlayConnectionTest {
    @Test
    public void storefrontSuccessAndRedirectCountAsReachable() throws Exception {
        for (int status : new int[]{200, 302}) {
            Response connection = new Response(status);
            assertTrue(GooglePlayConnection.isReachable(connection));
            assertEquals("HEAD", connection.getRequestMethod());
            assertFalse(connection.getInstanceFollowRedirects());
            assertFalse(connection.getUseCaches());
            assertTrue(connection.getConnectTimeout() > 0);
            assertTrue(connection.getReadTimeout() > 0);
            assertTrue(connection.disconnected);
        }
    }

    @Test
    public void failedResponseAndTimeoutCountAsUnreachable() throws Exception {
        for (int status : new int[]{403, 503, -1}) {
            Response connection = new Response(status);
            assertFalse(GooglePlayConnection.isReachable(connection));
            assertTrue(connection.disconnected);
        }
    }

    private static final class Response extends HttpsURLConnection {
        private final int status;
        private boolean disconnected;

        Response(int status) throws MalformedURLException {
            super(new URL("https://play.google.com/store"));
            this.status = status;
        }

        @Override public int getResponseCode() throws IOException {
            if (status == -1) throw new SocketTimeoutException();
            return status;
        }

        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
        @Override public String getCipherSuite() { return ""; }
        @Override public Certificate[] getLocalCertificates() { return null; }
        @Override public Certificate[] getServerCertificates() { return null; }
    }
}
