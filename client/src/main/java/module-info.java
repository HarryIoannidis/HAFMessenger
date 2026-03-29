module client {
    requires transitive shared;
    requires transitive javafx.graphics;
    requires transitive javafx.controls;
    requires transitive com.jfoenix;
    requires javafx.fxml;
    requires java.net.http;
    requires org.slf4j;
    requires org.slf4j.jul;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;
    requires java.prefs;
    requires java.desktop;

    exports com.haf.client.models;
    exports com.haf.client.controllers;
    exports com.haf.client.core;
    exports com.haf.client.utils;
    exports com.haf.client.crypto;
    exports com.haf.client.viewmodels;
    exports com.haf.client.network;
    exports com.haf.client.services;

    opens com.haf.client.controllers to javafx.fxml;
    opens com.haf.client.core to javafx.graphics;
}
