module server {
    requires com.zaxxer.hikari;
    requires flyway.core;
    requires java.sql;
    requires jdk.httpserver;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires org.java_websocket;
    requires transitive shared;
    requires io.github.cdimascio.dotenv.java;
}