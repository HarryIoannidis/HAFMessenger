module client {
    requires transitive shared;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;

    // exports com.haf.client.ui;
    // exports com.haf.client.controllers;
    // exports com.haf.client.service;
    // exports com.haf.client.utils;
    exports com.haf.client.crypto;
    exports com.haf.client.viewmodels;
    exports com.haf.client.network;

    // opens com.haf.client.controllers to javafx.fxml;
}
