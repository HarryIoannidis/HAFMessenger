# REGISTER

### Screen objective
- Multi-step registration flow collecting personal info, password, and E2E-encrypted identity photos.
- X25519 keypair generation during registration for future E2E messaging.
- Photo encryption using ephemeral X25519 ECDH + AES-256-GCM before server upload.

### FXML
- `register.fxml`

### Architecture
- **Controller**: `RegisterController`.
- **ViewModel**: `RegisterViewModel`.
- **Pattern**: MVVM with property binding, multi-step form.

### Registration steps

#### Step 1 — Personal Information
- `TextField fullNameField`: full name.
- `TextField regNumberField`: registration number.
- `TextField idNumberField`: ID card number.
- `ComboBox rankComboBox`: military rank (with custom cell rendering and rank icons).
- `TextField telephoneField`: phone number.
- `TextField emailField`: email address.
- Validation: all fields required, valid formats.

#### Step 2 — Password
- `PasswordField passwordField`: password input.
- `TextField passwordVisible`: visible password toggle.
- `PasswordField confirmPasswordField`: password confirmation.
- `TextField confirmPasswordVisible`: visible confirmation toggle.
- `JFXButton togglePasswordBtn` / `JFXButton togglePasswordConfBtn`: toggle visibility.
- Validation: password non-empty, minimum strength, confirmation matches.

#### Step 3 — ID Photo Upload
- `StackPane dropZone`: drag-and-drop or click-to-select photo.
- `ImageView photoPreview`: preview of selected photo.
- Supported formats: JPEG, PNG.
- Photo encrypted E2E before upload.

#### Step 4 — Selfie Photo Upload
- Same UI as Step 3 but for selfie photo.
- Photo encrypted E2E before upload.

### Flow
1. `RegisterController.initialize()`:
    - `initializeStep()`: set initial step, configure rank ComboBox.
    - `bindViewModel()`: bind all field properties and error properties.
    - `setupListeners()`: register button, back button, photo handlers.
    - `setupWindowControls()`: custom title bar.
2. User fills steps 1 → 2 → 3 → 4.
3. `handleRegister()`:
    - Validates current step via `viewModel.validateStep()`.
    - If invalid: shake animation on invalid fields.
    - If valid and not last step: transition to next step.
    - If valid and last step: `performRegistration()`.
4. `performRegistration()`:
    - Generate X25519 keypair via `EccKeyIO.generate()`.
    - Save keypair to local keystore via `KeystoreBootstrap`.
    - Encrypt ID photo via `encryptPhoto(file, adminPublicKey)`.
    - Encrypt selfie photo via `encryptPhoto(file, adminPublicKey)`.
    - Build `RegisterRequest` DTO with all fields + encrypted photos.
    - Send HTTPS POST to `/api/register` with JSON body.
    - On success: navigate to login screen.
    - On failure: show error dialog.
5. `handleBack()`: transition to previous step.

### Photo encryption
- `encryptPhoto(File file, PublicKey adminPublicKey)`:
    1. Generate ephemeral X25519 keypair.
    2. ECDH(ephemeral_private, admin_public) → shared secret.
    3. SHA-256 KDF → AES-256 session key.
    4. Generate 12-byte IV.
    5. AES-GCM encrypt file bytes + AAD.
    6. Return `EncryptedFileDTO` (ciphertextB64, ivB64, tagB64, ephemeralPublicB64, contentType, originalSize).

### Window style
- `StageStyle.TRANSPARENT` (undecorated, custom styling).
- Draggable title bar with back and close buttons.

### Security
- Password sent over TLS, server hashes with BCrypt.
- Photos encrypted E2E with admin's X25519 public key — server never sees plaintext.
- X25519 keypair generated locally, public key + fingerprint sent to server.
