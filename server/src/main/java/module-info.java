module server {
    requires flyway.core;
    requires password4j;
    requires com.zaxxer.hikari;
    requires java.sql;
    requires java.net.http;
    requires jdk.httpserver;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j.layout.template.json;
    requires io.github.cdimascio.dotenv.java;
    requires org.slf4j;
    requires org.java_websocket;
    requires transitive shared;

    opens com.haf.server.ingress to com.fasterxml.jackson.databind;
    opens com.haf.server.realtime to com.fasterxml.jackson.databind;
}
