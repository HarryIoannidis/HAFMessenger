package com.haf.client.utils;

import com.haf.client.exceptions.SslConfigurationException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Properties;

/**
 * Provides SSLContext initialization utilities for trusted client-server
 * communication.
 */
public class SslContextUtils {

    /**
     * Prevents instantiation of this utility class.
     *
     * @throws IllegalStateException always, because this class exposes only static
     *                               utility methods
     */
    private SslContextUtils() {
        throw new IllegalStateException("Utility class");
    }

    private static SSLContext devSslContext;
    private static SSLContext strictSslContext;

    /**
     * Loads the custom truststore and returns an SSLContext that trusts our
     * server's self-signed certificate.
     * The truststore password is read from /config/client.properties.
     */
    public static synchronized SSLContext getTrustingSslContext() {
        if (devSslContext != null) {
            return devSslContext;
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
            devSslContext = SSLContext.getInstance("TLSv1.3");
            devSslContext.init(null, tmf.getTrustManagers(), null);

            return devSslContext;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to initialize custom SSLContext", e);
        }
    }

    /**
     * Returns strict TLS SSLContext backed by the default JVM trust store.
     *
     * @return strict SSL context
     */
    public static synchronized SSLContext getStrictSslContext() {
        if (strictSslContext != null) {
            return strictSslContext;
        }
        try {
            strictSslContext = SSLContext.getDefault();
            return strictSslContext;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to initialize strict SSLContext", e);
        }
    }

    /**
     * Returns SSLContext for runtime mode.
     *
     * @param isDev whether dev mode is active
     * @return mode-specific SSL context
     */
    public static SSLContext getSslContextForMode(boolean isDev) {
        return isDev ? getTrustingSslContext() : getStrictSslContext();
    }

    /**
     * Creates TLS parameters with HTTPS endpoint identification enabled.
     *
     * @return HTTPS-capable SSL parameters
     */
    public static SSLParameters createHttpsSslParameters() {
        SSLParameters parameters = new SSLParameters();
        parameters.setProtocols(new String[] { "TLSv1.3" });
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        return parameters;
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
