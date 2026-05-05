module server {
    requires flyway.core;
    requires password4j;
    requires com.zaxxer.hikari;
    requires java.sql;
    requires jdk.httpserver;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j.layout.template.json;
    requires io.github.cdimascio.dotenv.java;
    requires org.slf4j;
    requires transitive shared;

    opens com.haf.server.ingress to com.fasterxml.jackson.databind;
}
