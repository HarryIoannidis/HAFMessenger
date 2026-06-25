# HAF Messenger Screen Tour

A visual walkthrough of the **HAF Secure Messenger** desktop client interface, its cryptographic settings, security features, and user workflows.

---

## 1. Startup & Authentication Flow

This flow guides the user from application launch, through registration, and into their secure session.

### Splash Screen & Initial Loading

When the application starts, it performs self-checks and bootstraps the internal cryptography and database synchronization modules.
![Splash Screen](.github/screenshots/splash_screen_loading.png)

### Startup Error Dialog

If an essential security component, local database migration, or server connection fails during startup, the application presents a recovery dialog.
![Startup Failed](.github/screenshots/startup_failed_dialog.png)

### Login Screen

The entry point for registered users. It requests credentials and offers local password caching options.
![Login Screen](.github/screenshots/login_screen.png)

### Login Input Validation

Real-time, client-side validation detects missing inputs and alerts the user prior to network requests.
![Login Validation](.github/screenshots/login_validation_error.png)

### Session Negotiation

Once credentials are submitted, the application negotiates the HTTPS session, retrieves the JWT tokens, and bootstraps local components.
![Signing In](.github/screenshots/login_signing_in.png)

### User Registration (Step 1)

New users specify their details, email, and choose their military rank to build their cryptographic signature identity.
![Registration Details](.github/screenshots/register_step1_details.png)

### Secure ID Upload (Step 2)

To verify military identity, the registration flow requires uploading a photo of their service ID card. This photo is encrypted client-side with the admin's public key before upload.
![ID Upload](.github/screenshots/register_step2_id_upload.png)

### Profile Picture Upload (Step 3)

The final step of registration prompts the user to upload their profile avatar.
![Self Picture Upload](.github/screenshots/register_step3_self_picture.png)

---

## 2. Core Messaging & Contacts

The main workspace where users communicate and manage contacts.

### Empty Messaging State

When first logged in, the application shows a clean layout encouraging the user to select an active contact or search for a new one.
![Main Chat Empty](.github/screenshots/chat_main_empty.png)

### Active Secure Conversation

An end-to-end encrypted messaging session. Features inline preview for image attachments, dedicated file attachment wrappers (e.g., PDF downloads), and cryptographic signature validation status.
![Active Conversation](.github/screenshots/chat_active_conversation.png)

### User Search Input

Users can search the server directory by name, rank, or registration number to start chats or send contact requests.
![Search Users Initial](.github/screenshots/search_users_initial.png)

### Search Results

Matched results showing details, rank icons, and quick actions to add contacts or initiate chats.
![Search Users Results](.github/screenshots/search_users_results.png)

---

## 3. Privacy & Security

Hardened features designed to protect conversations in public or shared physical environments.

### Focus Loss Blur Protection

When the application window loses focus, it immediately blurs all message bubbles, attachments, and usernames to prevent shoulder surfing.
![Chat Privacy Blurred](.github/screenshots/chat_privacy_blurred.png)

### Privacy Lock Enabled

If the lock is activated or the window is blurred, a modal covers the content requesting the user to explicitly unlock the application.
![Privacy Lock Dialog](.github/screenshots/privacy_lock_dialog.png)

---

## 4. User Profiles & Information

Inspecting identities and administrative fields in the application.

### Own User Profile

Displays registration details, cryptographic keys, rank, and features shortcuts to request modifications or account deletion.
![User Profile Information](.github/screenshots/user_profile_information.png)

### Contact Profile Information

Inspecting contact card details (such as rank, registration number, and contact info) for verified messaging partners.
![Contact Details Information](.github/screenshots/contact_details_information.png)

---

## 5. Application Settings

Fine-tuning notification, convenience, and safety settings.

### General Settings

Control confirm-on-exit actions, logout confirmations, and persistent window state behaviors.
![General Settings](.github/screenshots/settings_general.png)

### Privacy Settings

Adjust blur-on-focus loss strength, prompt preferences before launching attachments, and toggle message notifications.
![Privacy Settings](.github/screenshots/settings_privacy.png)

---

## 6. Dialogs & Prompts

A consistent set of user feedback confirmations for critical destructive actions.

### File Attachment Preview

Before downloading files, users can preview images/metadata and initiate a secure download.
![Attachment Preview Dialog](.github/screenshots/attachment_preview_download_dialog.png)

### Delete Chat History Confirmation

Prompt requiring explicit user confirmation before purging local encrypted database logs of a chat.
![Delete Chat Dialog](.github/screenshots/delete_chat_dialog.png)

### Remove Contact Confirmation

Prompt before removing a user from the local trusted contact directory.
![Remove Contact Dialog](.github/screenshots/remove_contact_dialog.png)

### Logout Confirmation

Prompt ensuring the user wants to invalidate their current session tokens and return to the login screen.
![Logout Dialog](.github/screenshots/logout_dialog.png)

### Exit Application Confirmation

Prompt verifying window termination intent to ensure background crypto modules terminate cleanly.
![Exit Application Dialog](.github/screenshots/exit_application_dialog.png)

---

## 7. Development & Scene Design

### JavaFX Scene Builder Integration

The UI layout is engineered visually using JavaFX FXML. The screenshot shows the login interface structure within Gluon Scene Builder.
![Scene Builder](.github/screenshots/scene_builder_login_fxml.png)
