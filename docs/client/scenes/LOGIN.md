# LOGIN

### Screen objective
- Authenticate the user with email and password.
- Validate input fields with visual error feedback (shake animation, error styling).
- Navigate to main chat on success, or to registration for new users.

### FXML
- `login.fxml`

### Architecture
- **Controller**: `LoginController`.
- **ViewModel**: `LoginViewModel`.
- **Pattern**: MVVM with property binding.

### UI elements
- `BorderPane rootContainer`: transparent stage with custom title bar.
- `HBox titleBar`: draggable custom title bar with window controls (minimize, maximize, close).
- `TextField emailField`: email input with error styling.
- `PasswordField passwordField`: password input (hidden by default).
- `TextField passwordVisible`: visible password toggle (swapped with `passwordField`).
- `JFXButton togglePasswordBtn`: toggle password visibility (eye icon).
- `JFXButton signInButton`: triggers login.
- `Text signUpLink`: navigates to register screen.
- `Text errorLabel`: displays validation/server error messages.

### Flow
1. `LoginController.initialize()`:
    - `bindViewModel()`: bind email/password properties and error properties.
    - `setupListeners()`: sign-in button, sign-up link, Enter key.
    - `setupInputInteractionListeners()`: clear errors on typing.
    - `setupValidationErrorListeners()`: apply error styling.
    - `setupPasswordToggle()`: eye icon toggles visibility.
    - `setupWindowControls()`: minimize/maximize/close buttons, title bar drag.
2. User enters email + password.
3. `handleSignIn()`:
    - `viewModel.validate()` → checks email format, password non-empty.
    - If invalid: shake animation on invalid fields, show errors.
    - If valid: send `LoginRequest` via HTTPS to server.
    - On success: initialize `ChatSession`, navigate to `main.fxml` via `ViewRouter.switchTo()`.
    - On failure: show error message.
4. `navigateToRegister()`: `ViewRouter.switchToTransparent("/fxml/register.fxml")`.

### Validation
- Email: non-empty, valid email format.
- Password: non-empty.
- Error feedback: red border on invalid fields, shake animation, error label text.

### Window style
- `StageStyle.TRANSPARENT` (undecorated, custom styling).
- Draggable title bar with custom minimize/maximize/close buttons.

### Security
- Password sent over TLS to server, server verifies against BCrypt hash.
- `SslContextUtils` configures TLS for HTTPS requests.
- Password visibility toggle for user convenience.
