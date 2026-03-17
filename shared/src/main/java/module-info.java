module shared {
    exports com.haf.shared.dto;
    exports com.haf.shared.requests;
    exports com.haf.shared.responses;
    exports com.haf.shared.constants;
    exports com.haf.shared.utils;
    exports com.haf.shared.crypto;
    exports com.haf.shared.exceptions;
    exports com.haf.shared.keystore;

    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
}
