## SPLASH SCREEN

### Screen objective
- To start the application with controlled loading of settings/resources and network checks before the Login is displayed.
- To show clear progress/status with secure preparations (TLS pinning readiness, crypto providers).

### UI elements (proposed)
- Horizontal ProgressBar, Label for messages (e.g. “Initializing crypto…”, “Contacting server…”).
- Optional: version Label (e.g. v1.0.0), unit insignia/logo.

### Flows and checks
- Load local settings (config), check available certificates/pins.
- Fast TLS reachability check of the server endpoint (without login).
- Preload basic JavaFX resources (styles, icons).

### Implementation (FXML + MVVM)
- FXML: splash.fxml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<BorderPane xmlns:fx="http://javafx.com/fxml" fx:controller="com.haf.client.ui.SplashController">
  <center>
    <VBox alignment="CENTER" spacing="12">
      <Label text="HAF Messenger"/>
      <ProgressBar fx:id="progress" prefWidth="320"/>
      <Label fx:id="status" text="Initializing..."/>
    </VBox>
  </center>
</BorderPane>
```
- Controller:
```java
public class SplashController {
  @FXML private ProgressBar progress; @FXML private Label status;
  private final SplashViewModel vm = new SplashViewModel();
  @FXML public void initialize(){
    status.textProperty().bind(vm.statusProperty());
    progress.progressProperty().bind(vm.progressProperty());
    vm.startBootstrap(()-> ViewRouter.gotoLogin());
  }
}
```
- ViewModel:
```java
public class SplashViewModel {
  private final StringProperty status = new SimpleStringProperty();
  private final DoubleProperty progress = new SimpleDoubleProperty(-1);
  public StringProperty statusProperty(){ return status; }
  public DoubleProperty progressProperty(){ return progress; }

  public void startBootstrap(Runnable onOk){
    Task<Void> t = new Task<>() {
      @Override protected Void call() throws Exception {
        update("Loading config...", 0.1);
        Config.load();
        update("Initializing crypto...", 0.3);
        Crypto.initProviders();
        update("Verifying server reachability...", 0.6);
        Network.quickTlsCheck();
        update("Preloading UI resources...", 0.8);
        Ui.preload();
        update("Ready", 1.0);
        return null;
      }
      private void update(String s, double p){ Platform.runLater(()->{ status.set(s); progress.set(p); }); }
    };
    t.setOnSucceeded(e-> onOk.run());
    new Thread(t, "bootstrap").start();
  }
}
```

### Security
- Crypto.initProviders(): installs JCA provider, checks “AES/GCM” availability, strong SecureRandom.
- Network.quickTlsCheck(): creates TLS context with pinning and performs HEAD/WS handshake to server; fails with clear messages.
- No sensitive information is loaded in plaintext; only readiness checks.

### Error handling
- On TLS/pinning failure: show dialog “Cannot verify server. Check certificate pin or network.” with Retry/Exit.
- Logging to local file for forensics (no secrets).

***

## LOGIN SCREEN

### Screen objective
- To perform reliable user authentication with minimal friction and immediate readiness for 2FA/TOTP where required.
- To enforce security policies: no plaintext password storage, “remember” option only via secure token/OS keystore, clear error messages.

### UI elements (proposed)
- TextField: Email or Username, PasswordField: Password, CheckBox: Remember credentials, Button: Sign In, Link: Register.
- Optional: small status label for “Connecting…/Authenticating…/TOTP required”.

### Flows and checks
- Field validation before call: email format, password length, no blanks.
- Call AuthService.login(username, password) over TLS 1.3 with pinning.
- If the account requires 2FA: transition to 2FA prompt (TOTP). Otherwise, transition to Main Chat.

### Implementation (FXML + MVVM)
- FXML: login.fxml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<GridPane xmlns:fx="http://javafx.com/fxml" fx:controller="com.haf.client.ui.LoginController" hgap="8" vgap="8">
  <Label text="Email"/>
  <TextField fx:id="email"/>
  <Label text="Password"/>
  <PasswordField fx:id="password"/>
  <CheckBox fx:id="remember" text="Remember credentials"/>
  <Button text="Sign In" onAction="#onSignIn"/>
  <Hyperlink text="Register" onAction="#onRegister"/>
  <Label fx:id="status" text=""/>
</GridPane>
```
- Controller:
```java
public class LoginController {
  @FXML private TextField email; @FXML private PasswordField password;
  @FXML private CheckBox remember; @FXML private Label status;
  private final LoginViewModel vm = new LoginViewModel();

  @FXML public void initialize(){
    status.textProperty().bind(vm.statusProperty());
    vm.rememberProperty().bind(remember.selectedProperty());
    vm.emailProperty().bindBidirectional(email.textProperty());
    vm.passwordProperty().bindBidirectional(password.textProperty());
  }

  @FXML private void onSignIn(){
    vm.signIn(
      ()-> ViewRouter.gotoMainChat(),
      ()-> ViewRouter.gotoTotpPrompt()
    );
  }
  @FXML private void onRegister(){ ViewRouter.gotoRegistration(); }
}
```
- ViewModel:
```java
public class LoginViewModel {
  private final StringProperty email = new SimpleStringProperty();
  private final StringProperty password = new SimpleStringProperty();
  private final BooleanProperty remember = new SimpleBooleanProperty(false);
  private final StringProperty status = new SimpleStringProperty();

  public StringProperty emailProperty(){ return email; }
  public StringProperty passwordProperty(){ return password; }
  public BooleanProperty rememberProperty(){ return remember; }
  public StringProperty statusProperty(){ return status; }

  public void signIn(Runnable onOk, Runnable onTotp){
    status.set("Authenticating...");
    Task<Void> t = new Task<>() {
      @Override protected Void call() throws Exception {
        AuthResult r = AuthService.login(email.get(), password.get());
        if (r.requiresTotp()) Platform.runLater(onTotp);
        else if (r.ok()) {
          if (remember.get()) SecureStore.persistToken(r.token());
          Platform.runLater(onOk);
        } else {
          Platform.runLater(()-> status.set("Invalid credentials"));
        }
        return null;
      }
    };
    new Thread(t, "login").start();
  }
}
```

### Security
- Never store the password on disk or in config; only in memory during the call.
- If Remember credentials is enabled, store only a secure session/refresh token in the OS keystore or encrypted with a system key.
- Rate limiting/lockout is handled by the server, with user-friendly but clear messages “Try again in X min” without leaking details.

### Error handling
- Network error: “Cannot reach server. Check connection.” with Retry.
- TOTP error: “Incorrect code. Please retry.” with countdown for new code.
- In general: no disclosure of internal causes (e.g. “user not found”), only neutral messages.

***

## REGISTER (Step 1: Details)

### Screen objective
- To collect verifiable personnel details with strict validation before account creation.
- To prepare for identity verification (step 2) and activation of 2FA after approval.

### UI elements
- Fields: Full Name, Rank, Reg. Number, Joined, Telephone, Email, Password, Confirm Password.
- Buttons: Next, Back (if returning from step 2), Cancel.
- Error indicators under each field with clear messages (e.g. “Invalid email”).

### Flows and checks
- Mandatory validation:
    - Email format, uniqueness (server-side check), required rank/registry number.
    - Password: minimum length, complexity (upper/lower/digit/symbol), match with confirm.
    - Phone: international/domestic format, optional masking.
- On Next:
    - Local field checks.
    - Optional server-side pre-check (without creating user yet) for email/registry number conflicts.
    - Storage of temporary RegistrationState on the client (in-memory).

### Implementation (FXML + MVVM)
- FXML: registration_step1.fxml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<GridPane xmlns:fx="http://javafx.com/fxml" fx:controller="com.haf.client.ui.RegStep1Controller" hgap="10" vgap="8">
  <Label text="Full Name"/><TextField fx:id="fullName"/>
  <Label text="Rank"/><TextField fx:id="rank"/>
  <Label text="Reg. Number"/><TextField fx:id="regNumber"/>
  <Label text="Joined"/><DatePicker fx:id="joined"/>
  <Label text="Telephone"/><TextField fx:id="telephone"/>
  <Label text="Email"/><TextField fx:id="email"/>
  <Label text="Password"/><PasswordField fx:id="password"/>
  <Label text="Confirm Password"/><PasswordField fx:id="confirm"/>
  <HBox spacing="8">
    <Button text="Cancel" onAction="#onCancel"/>
    <Region HBox.hgrow="ALWAYS"/>
    <Button text="Next" onAction="#onNext"/>
  </HBox>
</GridPane>
```
- Controller:
```java
public class RegStep1Controller {
  @FXML private TextField fullName, rank, regNumber, telephone, email;
  @FXML private DatePicker joined;
  @FXML private PasswordField password, confirm;

  private final RegistrationViewModel vm = RegistrationViewModel.instance();

  @FXML private void onNext(){
    vm.setBasicInfo(fullName.getText(), rank.getText(), regNumber.getText(),
                    joined.getValue(), telephone.getText(), email.getText(),
                    password.getText(), confirm.getText());
    if (vm.validateStep1()) ViewRouter.gotoRegStep2();
  }
  @FXML private void onCancel(){ ViewRouter.gotoLogin(); }
}
```
- ViewModel (indicative validation):
```java
public boolean validateStep1(){
  if (!email.get().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) { errors.add("Invalid email"); return false; }
  if (!password.get().equals(confirm.get())) { errors.add("Passwords mismatch"); return false; }
  if (!PasswordPolicies.isStrong(password.get())) { errors.add("Weak password"); return false; }
  return true;
}
```

### Security
- No password stored on disk; only transient until submit.
- Server-side hashing (Argon2/bcrypt) applied only after final submission (at end of step 2).
- Audit trail: logging of registration request (without plaintext).

### Error handling
- Field-specific messages, not generic alerts.
- On email/registry conflict: indication on the corresponding field and blocking of continuation.

***

## REGISTER (Step 2: Uploads – Drag & Drop)

### Screen objective
- To collect identity proof (ID, Selfie) with secure transfer and temporary storage.
- To complete registration by submitting all data.

### UI elements
- Two drop zones: Identity Card (ID) and Selfie, with format/size instructions.
- Image preview, Remove/Replace buttons, upload progress.
- Buttons: Back, Submit.

### Flows and checks
- Drag & drop or file chooser; immediate local preview and type check (JPEG/PNG/PDF for ID, JPEG/PNG for selfie).
- Size check (e.g. ≤ 5MB), automatic client-side scaling/compression where needed.
- On Submit:
    - Combine Step1 data + files.
    - Client-side encryption (AES-GCM) before upload.
    - Upload to server with TTL and return of Registration-Receipt.

### Implementation (FXML + MVVM)
- FXML: registration_step2.fxml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<VBox xmlns:fx="http://javafx.com/fxml" fx:controller="com.haf.client.ui.RegStep2Controller" spacing="12">
  <Label text="Upload Identity Card (ID)"/>
  <StackPane fx:id="idDropZone" styleClass="drop-zone"/>
  <Label text="Upload Selfie"/>
  <StackPane fx:id="selfieDropZone" styleClass="drop-zone"/>
  <HBox spacing="8">
    <Button text="Back" onAction="#onBack"/>
    <Region HBox.hgrow="ALWAYS"/>
    <Button text="Submit" onAction="#onSubmit"/>
  </HBox>
</VBox>
```
- Controller (drag & drop events):
```java
public class RegStep2Controller {
  @FXML private StackPane idDropZone, selfieDropZone;
  private final RegistrationViewModel vm = RegistrationViewModel.instance();

  @FXML public void initialize(){
    enableDnD(idDropZone, vm::setIdFile);
    enableDnD(selfieDropZone, vm::setSelfieFile);
  }

  private void enableDnD(StackPane zone, Consumer<File> setter){
    zone.setOnDragOver(e->{ if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY); e.consume(); });
    zone.setOnDragDropped(e->{
      File f = e.getDragboard().getFiles().get(0);
      if (RegistrationViewModel.isAllowed(f)) { setter.accept(f); /* update preview */ }
      e.setDropCompleted(true); e.consume();
    });
  }

  @FXML private void onBack(){ ViewRouter.gotoRegStep1(); }

  @FXML private void onSubmit(){
    if (!vm.validateStep2()) return;
    vm.submitAll(() -> ViewRouter.gotoLogin());
  }
}
```
- ViewModel (encrypted upload):
```java
public void submitAll(Runnable onDone){
  Task<Void> t = new Task<>() {
    @Override protected Void call() throws Exception {
      byte[] idEnc = Crypto.encryptFile(idFile.get());
      byte[] selfieEnc = Crypto.encryptFile(selfieFile.get());
      RegistrationPayload payload = buildPayload(idEnc, selfieEnc, /* from step1 fields */);
      RegistrationResult res = RegistrationService.submit(payload);
      if (res.ok()) Platform.runLater(onDone);
      return null;
    }
  };
  new Thread(t, "reg-submit").start();
}
```

### Security
- Local content checks (type/size), blocking dangerous files.
- Client-side encryption before transfer, server stores only ciphertext with TTL and metadata.
- Audit: logging submissions/failures without sensitive fields in plaintext.

### Error handling
- Upload failure: show Retry, keeping selected files in memory.
- Invalid type/size: indication on drop zone and blocking Submit.
- Race-condition protection: disable buttons during submit, visible progress indicator.

--- 

## MAIN CHAT (No chat selected – overflow “⋯”)

### Screen objective
- To provide an operational overview of personnel with presence indication and immediate search, while the central canvas remains in standby with “No chat selected…” and “Start by opening a chat.” until a contact is selected.
- To minimize visual noise in the header via overflow button “⋯” that groups Profile, Settings, Help, Log out.

### UI elements (proposed)
- Contacts sidebar (left):
    - Action icons (chat, search) at the top of the list.
    - Contact cards with circular avatar, full name, status line Active/Inactive and green/red presence dot.
    - Vertical scrolling for large lists.
- Central canvas:
    - Centered labels “No chat selected…” and “Start by opening a chat.” with accompanying illustration.
- Header (top right):
    - Window controls (maximize, full-screen, close) and overflow “⋯” for drop-down commands.

### Flows and checks
- Presence load/update:
    - Initial snapshot from PresenceService and continuous heartbeats for Active/Inactive changes; immediate dot updates.
- Contact search:
    - Clicking the search icon focuses the search field and applies dynamic filter on names/ranks with 200 ms debounce.
- Contact selection:
    - Click on card → replace placeholder with the ChatArea of the specific conversation (history, composer, attachments).
- Overflow “⋯”:
    - Open ContextMenu with Profile, Settings, Help, Log out and handle navigation/session cleanup.

### Implementation (FXML + MVVM)
- FXML: main_chat.fxml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<BorderPane xmlns:fx="http://javafx.com/fxml" fx:controller="com.haf.client.ui.MainChatController">
  <left>
    <VBox spacing="6">
      <HBox spacing="10">
        <Button text="💬" onAction="#focusContacts"/>
        <Button text="🔍" onAction="#focusSearch"/>
      </HBox>
      <TextField fx:id="search" promptText="Search personnel..."/>
      <ListView fx:id="contacts"/>
    </VBox>
  </left>
  <top>
    <HBox alignment="CENTER_RIGHT" spacing="8">
      <Button text="⋯" onAction="#openOverflow"/>
    </HBox>
  </top>
  enterer>
    <StackPane fx:id="centerArea">
      <VBox alignment="CENTER" spacing="10">
        <Label text="No chat selected..."/>
        <Label text="Start by opening a chat."/>
      </VBox>
    </StackPane>
  </center>
</BorderPane>
```
- Controller:
```java
public class MainChatController {
  @FXML private TextField search; @FXML private ListView<UserVM> contacts;
  @FXML private StackPane centerArea; private ContextMenu menu;
  private final MainChatViewModel vm = new MainChatViewModel();

  @FXML public void initialize(){
    contacts.setItems(vm.filteredUsers());
    contacts.setCellFactory(lv-> new UserCell());
    search.textProperty().addListener((o,old,v)-> vm.setQuery(v));
    contacts.getSelectionModel().selectedItemProperty().addListener((o,old,sel)-> {
      if (sel!=null) ViewRouter.loadChatArea(centerArea, sel.userId());
    });
    menu = new ContextMenu(
      mi("Profile", ()-> ViewRouter.gotoProfile()),
      mi("Settings", ()-> ViewRouter.gotoSettings()),
      mi("Help", ()-> ViewRouter.gotoHelp()),
      new SeparatorMenuItem(),
      mi("Log out", ()->{ Session.end(); ViewRouter.gotoLogin(); })
    );
  }

  @FXML private void openOverflow(){
    Node n = ((HBox)((BorderPane)centerArea.getScene().getRoot()).getTop()).lookupButton("⋯");
    if (menu.isShowing()) menu.hide(); else menu.show(n, Side.BOTTOM, 0, 0);
  }
  private MenuItem mi(String t, Runnable r){ var m=new MenuItem(t); m.setOnAction(e->r.run()); return m; }
}
```
- ViewModel:
```java
public class MainChatViewModel {
  private final ObservableList<UserVM> users = FXCollections.observableArrayList();
  private final FilteredList<UserVM> filtered = new FilteredList<>(users, u->true);

  public MainChatViewModel(){
    users.setAll(PresenceService.initialSnapshot());
    PresenceService.onUpdate(this::applyPresence);
  }
  public void setQuery(String q){
    String s = q==null? "" : q.toLowerCase();
    filtered.setPredicate(u -> s.isBlank() ||
      u.displayName().toLowerCase().contains(s) ||
      u.rank().toLowerCase().contains(s));
  }
  public ObservableList<UserVM> filteredUsers(){ return filtered; }
  private void applyPresence(PresenceUpdate u){ /* update user.active */ }
}
```

### Security
- No display of conversation content without contact selection; only names and presence metadata.
- On Log out, clear session tokens/keys, terminate WebSocket, zero caches.

### Error handling
- Loss of presence channel: “Reconnecting…” banner and automatic retry with exponential backoff; list remains functional with last snapshot.
- Snapshot load failure: show cached data with “stale” indication and Retry button.

### Best practices
- Virtualized ListView for performance on large sets, lazy avatars, and debounced search 150–250 ms.
- Accessibility: keyboard navigation, focus management on overflow, aria labels for status dots.

***

## MAIN CHAT (Active conversation)

### Screen objective
- To exchange encrypted 1:1 messages with immediate visual feedback (sending/sent/failed), timestamps, and attachments support.
- To maintain high availability: continue operating during temporary network loss with outgoing queue and automatic retry.

### UI elements (proposed)
- Left: contacts list as before, with current selection highlighted.
- Center: history with bubbles for incoming (left) and outgoing (right), timestamps, and status indicators.
- Bottom: composer with TextArea “Write Message Here …”, Send button, Attach button (files/images), drag & drop.

### Flows and checks
- Message sending:
    - Enter → send, Shift+Enter → new line, spam throttle (e.g. 5 msgs/sec).
    - Pipeline: create IV, AES-256-GCM encrypt payload, wrap session key for recipient, send via WebSocket.
- Message reception:
    - Local decryption, GCM tag check; on failure, discard and notify user.
- Attachments:
    - Choose or drop → client-side chunked encryption, upload, show progress bubble then reference to recipient.

### Implementation (FXML + MVVM)
- FXML: chat_area.fxml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<VBox xmlns:fx="http://javafx.com/fxml" fx:controller="com.haf.client.ui.ChatController" spacing="6">
  <ScrollPane fx:id="scroll" fitToWidth="true">
    <VBox fx:id="messages" spacing="4"/>
  </ScrollPane>
  <HBox spacing="8">
    <TextArea fx:id="input" promptText="Write Message Here ..." prefRowCount="2"/>
    <Button text="Attach" onAction="#onAttach"/>
    <Button text="Send" onAction="#onSend"/>
  </HBox>
</VBox>
```
- Controller:
```
public class ChatController {
@FXML private VBox messages; @FXML private TextArea input; @FXML private ScrollPane scroll;
private final ChatViewModel vm = new ChatViewModel();

@FXML public void initialize(){
vm.messagesProperty().addListener((ListChangeListener<MessageVM>) c-> render());
input.setOnKeyPressed(e->{ if (e.getCode()==KeyCode.ENTER && !e.isShiftDown()){ e.consume(); onSend(); }});
enableDnD();
}
private void render(){ messages.getChildren().setAll(vm.renderedNodes()); Platform.runLater(()-> scroll.setVvalue(1.0)); }
@FXML private void onSend(){ String txt = input.getText().trim(); if (!txt.isEmpty()) { vm.sendText(txt); input.clear(); } }
@FXML private void onAttach(){ File f = FileDialogs.choose(); if (f!=null) vm.attachFile(f); }
private void enableDnD(){ messages.getScene().setOnDragDropped(e->{ /* forward to vm.attachFile */ }); }
}
```
- ViewModel:
```
public class ChatViewModel {
private final ObservableList<MessageVM> items = FXCollections.observableArrayList();
public ObservableList<MessageVM> messagesProperty(){ return items; }

public void sendText(String text){
MessageVM m = MessageVM.outgoingText(text);
items.add(m);
CryptoPipeline.encryptAndSend(m, res -> m.markSent(res.ok()), err -> m.markFailed(err));
}
public void attachFile(File f){
MessageVM m = MessageVM.outgoingFile(f);
items.add(m);
CryptoPipeline.encryptAndUpload(f, m::updateProgress, ref -> {
CryptoPipeline.sendFileRef(ref, r -> m.markSent(true), e -> m.markFailed(e));
}, e -> m.markFailed(e));
}
public List<Node> renderedNodes(){ return items.stream().map(MessageCellFactory::nodeFor).toList(); }
}
```

### Security
- End-to-end content encryption, server stores only ciphertext and metadata (timestamps, sizes).
- Per-message IVs, forward secrecy via per-session keys and periodic renewal.
- Filename sanitization, blocking dangerous types, anti-malware scanning at the perimeter (server-side policy).

### Error handling
- Network/Timeout: “sending” state with retries (exponential backoff), “Reconnecting…” banner when socket drops.
- Decryption failure: visible “Decryption failed — message discarded” only to the recipient.
- Upload failure: retry capability in the same bubble, preserve selected file in memory.

### Best practices
- Virtualized history rendering, lazy fetch of older messages on scroll-up, client-side image compression.
- Shortcuts: Ctrl+E focus composer, Ctrl+K search contacts, Esc close overflow.

---

## PROFILE SCREEN

### Screen objective
- To review and controlled update of user personal details, with change request and approval process where required.
- To display operational identifiers (rank, registry number, date of joining) without exposing sensitive data to third parties.

### UI elements (proposed)
- Read-only fields: Full Name, Rank, Reg. Number, Joined, Telephone, Email.
- Buttons: Request Edit to submit a change request, Back to return to Main Chat.
- Indicative badges/labels for request status: Pending, Approved, Rejected.

### Flows and checks
- Profile loading:
  - Retrieve details via ProfileService over TLS 1.3 with pinning and cache only non-sensitive fields.
- Change request:
  - Request Edit opens modal with editable fields, validation (email/phone), and submission to server for approval.
- Request status:
  - Display current status and last update, with possible Withdraw before approval.

### Implementation (FXML + MVVM)
- FXML: profile.fxml
```
<?xml version="1.0" encoding="UTF-8"?>
<VBox xmlns:fx="http://javafx.com/fxml" fx:controller="com.haf.client.ui.ProfileController" spacing="10">
  <GridPane hgap="10" vgap="8">
    <Label text="Full Name"/><Label fx:id="fullName"/>
    <Label text="Rank"/><Label fx:id="rank"/>
    <Label text="Reg. Number"/><Label fx:id="regNumber"/>
    <Label text="Joined"/><Label fx:id="joined"/>
    <Label text="Telephone"/><Label fx:id="telephone"/>
    <Label text="Email"/><Label fx:id="email"/>
  </GridPane>
  <HBox spacing="8">
    <Button text="Back" onAction="#onBack"/>
    <Region HBox.hgrow="ALWAYS"/>
    <Button text="Request Edit" onAction="#onRequestEdit"/>
  </HBox>
  <Label fx:id="requestStatus" />
</VBox>
```
- Controller:
```
public class ProfileController {
  @FXML private Label fullName, rank, regNumber, joined, telephone, email, requestStatus;
  private final ProfileViewModel vm = new ProfileViewModel();

@FXML public void initialize(){
vm.fullNameProperty().addListener((o,old,v)-> fullName.setText(v));
vm.rankProperty().addListener((o,old,v)-> rank.setText(v));
vm.regNumberProperty().addListener((o,old,v)-> regNumber.setText(v));
vm.joinedProperty().addListener((o,old,v)-> joined.setText(v));
vm.telephoneProperty().addListener((o,old,v)-> telephone.setText(v));
vm.emailProperty().addListener((o,old,v)-> email.setText(v));
vm.requestStatusProperty().addListener((o,old,v)-> requestStatus.setText(v));
vm.loadProfile();
}

@FXML private void onBack(){ ViewRouter.gotoMainChat(); }
@FXML private void onRequestEdit(){ ViewRouter.openProfileEditDialog(vm); }
}
```
- ViewModel:
```
public class ProfileViewModel {
private final StringProperty fullName=new SimpleStringProperty(), rank=new SimpleStringProperty(),
regNumber=new SimpleStringProperty(), joined=new SimpleStringProperty(),
telephone=new SimpleStringProperty(), email=new SimpleStringProperty(),
requestStatus=new SimpleStringProperty();

public void loadProfile(){
Task<ProfileDTO> t = new Task<>() {
@Override protected ProfileDTO call() { return ProfileService.fetch(); }
};
t.setOnSucceeded(e-> apply(t.getValue()));
new Thread(t,"profile-load").start();
}
private void apply(ProfileDTO p){
fullName.set(p.fullName); rank.set(p.rank); regNumber.set(p.regNumber);
joined.set(p.joinedDate); telephone.set(p.telephone); email.set(p.email);
requestStatus.set(p.requestStatus);
}
// getters properties...
}
```

### Security
- Show only necessary fields; masking of personal phone/email parts where required by policy.
- Change requests audited with user id, timestamp, and request hash, not plaintext-sensitive data in logs.
- Transfers exclusively via TLS 1.3 and certificate pinning from the client.

### Error handling
- Profile load failure: informational banner and Retry button, without freezing the UI.
- Request submission failure: keep local changes in modal, clear feedback “Submission failed — retry later”.

### Best practices
- Use immutable DTOs and explicit mapping to ViewModel, so domain entities do not “leak” into the UI.
- Throttle edit requests (e.g. 1/30 s) to avoid spam/accidental double submits.
- Visible Pending/Approved/Rejected status with color labels and tooltips.