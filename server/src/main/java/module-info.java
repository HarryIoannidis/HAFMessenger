module server {
    requires com.zaxxer.hikari;
    requires flyway.core;
    requires java.sql;
    requires jdk.httpserver;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires org.java_websocket;
    requires io.github.cdimascio.dotenv.java;
    requires password4j;
    requires transitive shared;

    opens com.haf.server.ingress to com.fasterxml.jackson.databind;
}
