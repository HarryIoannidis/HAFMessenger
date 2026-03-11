module client {
    requires transitive shared;
    requires transitive javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires java.logging;
    requires com.jfoenix;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;
    requires java.prefs;

    // exports com.haf.client.ui;
    // exports com.haf.client.service;
    exports com.haf.client.models;
    exports com.haf.client.controllers;
    exports com.haf.client.core;
    exports com.haf.client.utils;
    exports com.haf.client.crypto;
    exports com.haf.client.viewmodels;
    exports com.haf.client.network;

    opens com.haf.client.controllers to javafx.fxml;
    opens com.haf.client.core to javafx.graphics;
}
