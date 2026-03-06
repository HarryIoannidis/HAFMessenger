package com.haf.client.utils;

import com.haf.shared.exceptions.SslConfigurationException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Properties;

public class SslContextUtils {

    private SslContextUtils() {
        throw new IllegalStateException("Utility class");
    }

    private static SSLContext sslContext;

    /**
     * Loads the custom truststore and returns an SSLContext that trusts our
     * server's self-signed certificate.
     * The truststore password is read from /config/client.properties.
     */
    public static synchronized SSLContext getTrustingSslContext() {
        if (sslContext != null) {
            return sslContext;
        }

        try {
            // Load truststore password from properties file
            char[] truststorePassword = loadTruststorePassword();

            // Load the truststore from the classpath (resources/config/truststore.p12)
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (InputStream in = SslContextUtils.class.getResourceAsStream("/config/truststore.p12")) {
                if (in == null) {
                    throw new SslConfigurationException("Could not find /config/truststore.p12 in resources.");
                }
                trustStore.load(in, truststorePassword);
            }

            // Initialize a TrustManagerFactory with the loaded truststore
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            // Create and initialize the SSLContext
            sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(null, tmf.getTrustManagers(), null);

            return sslContext;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to initialize custom SSLContext", e);
        }
    }

    /**
     * Reads the truststore password from /config/client.properties on the
     * classpath.
     */
    private static char[] loadTruststorePassword() throws IOException {
        Properties props = new Properties();
        try (InputStream in = SslContextUtils.class.getResourceAsStream("/config/client.properties")) {
            if (in == null) {
                throw new SslConfigurationException("Could not find /config/client.properties in resources.");
            }
            props.load(in);
        }
        String password = props.getProperty("truststore.password");
        if (password == null || password.isBlank()) {
            throw new SslConfigurationException("Missing 'truststore.password' in client.properties.");
        }
        return password.toCharArray();
    }
}