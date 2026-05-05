package com.haf.client.utils;

import com.haf.client.exceptions.SslConfigurationException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * Provides strict TLS SSLContext initialization utilities.
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

    private static SSLContext strictSslContext;

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

}
