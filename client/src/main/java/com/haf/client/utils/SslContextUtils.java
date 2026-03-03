package com.haf.client.utils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

public class SslContextUtils {

    private SslContextUtils() {
        throw new IllegalStateException("Utility class");
    }

    private static SSLContext sslContext;

    /**
     * Loads the custom truststore and returns an SSLContext that trusts our
     * server's
     * self-signed certificate.
     */
    public static synchronized SSLContext getTrustingSslContext() {
        if (sslContext != null) {
            return sslContext;
        }

        try {
            // Load the truststore from the classpath (resources/config/truststore.p12)
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (InputStream in = SslContextUtils.class.getResourceAsStream("/config/truststore.p12")) {
                if (in == null) {
                    throw new RuntimeException("Could not find /config/truststore.p12 in resources.");
                }
                trustStore.load(in, "changeit".toCharArray());
            }

            // Initialize a TrustManagerFactory with the loaded truststore
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            // Create and initialize the SSLContext
            sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(null, tmf.getTrustManagers(), null);

            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize custom SSLContext", e);
        }
    }
}
