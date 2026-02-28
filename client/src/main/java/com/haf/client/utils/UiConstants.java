package com.haf.client.utils;

/**
 * UI-related constants for resource paths and common labels.
 */
public final class UiConstants {

    // FXML paths
    public static final String FXML_SPLASH = "/fxml/splash.fxml";
    public static final String FXML_LOGIN = "/fxml/login.fxml";
    public static final String FXML_REGISTER = "/fxml/register.fxml";
    public static final String FXML_MAIN = "/fxml/main.fxml";
    public static final String FXML_CHAT = "/fxml/chat.fxml";
    public static final String FXML_PLACEHOLDER = "/fxml/place_holder.fxml";
    public static final String FXML_CONTACT_CELL = "/fxml/contact_cell.fxml";

    // Images
    public static final String IMAGE_APP_LOGO = "/images/logo/app_logo.png";
    public static final String IMAGE_APP_LOGO_DOWNSCALE = "/images/logo/app_logo_downscale.png";
    public static final String IMAGE_APP_LOGO_UPSCALE = "/images/logo/app_logo_upscale.png";
    public static final String IMAGE_APP_LOGO_SVG = "/images/logo/app_logo_svg.svg";
    public static final String IMAGE_LOGO_PNG = "/images/logo/logoPNG.png";
    public static final String IMAGE_AVATAR = "/images/misc/avatar.png";
    public static final String IMAGE_EMPTY_CHAT = "/images/misc/empty_chat.png";

    // Rank names
    public static final String RANK_YPOSMINIAS = "Υποσμηνίας";
    public static final String RANK_SMINIAS = "Σμηνίας";
    public static final String RANK_EPISMINIAS = "Επισμηνίας";
    public static final String RANK_ARCHISMINIAS = "Αρχισμηνίας";
    public static final String RANK_ANTHYPASPISTIS = "Ανθυπασπιστής";
    public static final String RANK_ANTHYPOSMINAGOS = "Ανθυποσμηναγός";
    public static final String RANK_YPOSMINAGOS = "Υποσμηναγός";
    public static final String RANK_EPISMINAGOS = "Επισμηναγός";
    public static final String RANK_ANTISMINARCHOS = "Αντισμήναρχος";
    public static final String RANK_SMINARCHOS = "Σμήναρχος";
    public static final String RANK_TAKSIARCOS = "Ταξίαρχος";
    public static final String RANK_YPOPTERARCHOS = "Υποπτέραρχος";
    public static final String RANK_ANTIPTERARCHOS = "Αντιπτέραρχος";

    // Rank Icons
    public static final String ICON_RANK_YPOSMINIAS = "/images/ranks/sminias.png";
    public static final String ICON_RANK_SMINIAS = "/images/ranks/episminias.png";
    public static final String ICON_RANK_EPISMINIAS = "/images/ranks/arxisminias.png";
    public static final String ICON_RANK_ARCHISMINIAS = "/images/ranks/anthipaspistis.png";
    public static final String ICON_RANK_ANTHYPASPISTIS = "/images/ranks/anthiposminagos.png";
    public static final String ICON_RANK_ANTHYPOSMINAGOS = "/images/ranks/yposminagos.png";
    public static final String ICON_RANK_YPOSMINAGOS = "/images/ranks/sminagos.png";
    public static final String ICON_RANK_EPISMINAGOS = "/images/ranks/episminagos.png";
    public static final String ICON_RANK_ANTISMINARCHOS = "/images/ranks/antisminarxos.png";
    public static final String ICON_RANK_SMINARCHOS = "/images/ranks/sminarxos.png";
    public static final String ICON_RANK_TAKSIARCOS = "/images/ranks/taxiarxos.png";
    public static final String ICON_RANK_YPOPTERARCHOS = "/images/ranks/ypopterarxos.png";
    public static final String ICON_RANK_ANTIPTERARCHOS = "/images/ranks/antipterarxos.png";
    public static final String ICON_RANK_DEFAULT = "/images/ranks/sminias.png";

    // Bootstrap status messages
    public static final String BOOTSTRAP_STARTING = "Starting...";
    public static final String BOOTSTRAP_CONFIG = "Loading configuration...";
    public static final String BOOTSTRAP_SECURITY = "Initializing security modules...";
    public static final String BOOTSTRAP_RESOURCES = "Checking local resources...";
    public static final String BOOTSTRAP_NETWORK = "Verifying network reachability...";
    public static final String BOOTSTRAP_READY = "Ready";
    public static final String BOOTSTRAP_FAILED = "Initialization failed.";

    // Dialogs
    public static final String DIALOG_INIT_FAILED_TITLE = "Initialization failed";
    public static final String DIALOG_INIT_FAILED_HEADER = "Startup could not complete";

    // Stylesheets
    public static final String CSS_GLOBAL = "/css/global.css";
    public static final String CSS_LOGIN = "/css/login.css";
    public static final String CSS_REGISTER = "/css/register.css";
    public static final String CSS_MAIN = "/css/main.css";
    public static final String CSS_SPLASH = "/css/splash.css";

    // Fonts
    public static final String FONT_MANROPE = "/fonts/Manrope.ttf";
    public static final String FONT_MANROPE_BOLD = "/fonts/Manrope-Bold.ttf";

    public static final int FONT_SIZE_REGULAR = 18;
    public static final int FONT_SIZE_BOLD = 22;

    // Window/App titles
    public static final String APP_TITLE = "HAF Messenger";

    // Common Dimensions
    public static final double RANK_ICON_SIZE = 24.0;

    // Styles
    public static final String STYLE_TEXT_FIELD_ERROR = "text-field-error";
    public static final String STYLE_PASSWORD_FIELD_ERROR = "password-field-error";
    public static final String STYLE_BORDER_ERROR = "-fx-border-color: red; -fx-border-style: dashed; -fx-border-width: 2;";

    private UiConstants() {
    }
}
