module server {
    requires com.zaxxer.hikari;
    requires flyway.core;
    requires java.sql;
    requires jdk.httpserver;
    requires org.apache.logging.log4j;
    requires org.java_websocket;
    requires shared;

    exports com.haf.server.config;
    exports com.haf.server.metrics;
    exports com.haf.server.core;
    exports com.haf.server.router;
    exports com.haf.server.ingress;
    exports com.haf.server.db;
    exports com.haf.server.handlers;
}