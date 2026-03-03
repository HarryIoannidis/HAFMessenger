# DEVELOPMENT

This document describes the recommended workflow for adding new features or UI modules to **HAF Messenger**.
It follows an **MVVM (Model–View–ViewModel)** structure with a secure **Client–Server** architecture.

---

## **1. Design the Scene (UI Layer)**

* Use **Scene Builder** to create a new FXML file under `client/resources/fxml/`.
* Add your layout components and assign each element an `fx:id`.
* Set the controller to a new class under `com.haf.client.ui`.
* Keep the layout modular and clean (AnchorPane or GridPane recommended).

**Example**:
`client/resources/fxml/featureName.fxml`

---

## **2. Style the Interface (CSS Layer)**

* Create a matching `.css` file inside `client/resources/css/`.
* Define consistent colors, button states, and fonts that match the **dark military UI** theme.
* Attach the stylesheet to the FXML either in Scene Builder or dynamically in code.

**Example:**

```css
.root {
    -fx-background-color: #1b1f27;
}
.button-primary {
    -fx-background-color: #2e3b4e;
    -fx-text-fill: #e0e0e0;
}
```

---

## **3. Implement the Controller (UI Logic)**

* Create a corresponding controller class under `client/ui/`.
* Handle only **UI events** (button clicks, form submissions).
* Do **not** implement logic or data handling here — delegate that to the ViewModel.

**Example:**

```java
public class FeatureController {
    private final FeatureViewModel viewModel = new FeatureViewModel();

    @FXML private TextField inputField;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        statusLabel.textProperty().bind(viewModel.statusProperty());
    }

    @FXML
    public void onActionClick() {
        viewModel.processInput(inputField.getText());
    }
}
```

---

## **4. Build the ViewModel (Application Logic)**

* Add a new class in `client/viewmodel/`.
* Handle all **data operations**, **network calls**, and **crypto** interactions.
* Expose **JavaFX Properties** for two-way data binding with the UI.

**Example:**

```java
public class FeatureViewModel {
    private final StringProperty status = new SimpleStringProperty();

    public void processInput(String input) {
        status.set("Processing: " + input);
        // Perform validation, encryption, and network calls
    }

    public StringProperty statusProperty() { return status; }
}
```

---

## **5. Integrate Network Logic (Client-Side)**

* Implement network communication in `client/network/`.
* This layer connects to the server through **sockets** or **WebSockets**.
* Send/receive **DTOs** (Data Transfer Objects) defined in the `shared/` module.

**Example:**

```java
public class ClientConnection {
    public ResponseDTO send(RequestDTO request) {
        // serialize and transmit request to server
    }
}
```

---

## **6. Add Server Handler (Server-Side)**

* Create a matching handler in `server/handlers/`.
* Authenticate, validate, and process the incoming request.
* Return a serialized **ResponseDTO** to the client.

**Example:**

```java
public class FeatureHandler {
    public ResponseDTO handle(RequestDTO request) {
        // Execute business logic and return result
        return new ResponseDTO("ok", "Request processed");
    }
}
```

---

## **7. Define Shared DTOs and Utilities**

* Define your **RequestDTO**, **ResponseDTO**, and constants in the `shared/` module.
* Use these to standardize data exchange between client and server.

**Example:**

```java
public class RequestDTO implements Serializable {
    private String command;
    private String payload;
}
```

---

## **8. Secure the Communication (Crypto Layer)**

* Use modules in `client/crypto/` and `server/crypto/` for encryption.
* Apply **X25519** for key agreement and **AES** for message encryption.
* Store shared helpers (base64 encoding, key generation) in `shared/utils/`.

---

## **9. Test the Flow**

For each new feature, verify that the full data path works:

```
UI (FXML)
 → Controller 
 → ViewModel 
 → Network (Client)
 → Server Handler 
 → Response 
 → ViewModel Update 
 → UI Refresh
```

---

## **10. Expand Consistently**

When adding new functionality:

* Reuse shared components and styles.
* Maintain one FXML–Controller–ViewModel trio per feature.
* Follow the same directory and naming conventions.

