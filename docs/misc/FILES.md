# FILES

## client/src/main/java

## module-info.java

*Module descriptor for the client module*

## com/haf/client/controllers

* ChatController.java — *Controller for the chat view*
* ContactCell.java — *Custom ListCell for displaying contacts*
* LoginController.java — *Controller for login view*
* MainController.java — *Controller for the main application shell*
* RegisterController.java — *Controller for registration view*
* SplashController.java — *Controller for splash screen*

## com/haf/client/core

* ChatSession.java — *Container for the active chat session context*
* ClientApp.java — *Main client application class*
* Launcher.java — *Application launcher*

## com/haf/client/crypto

* UserKeystoreKeyProvider.java — *Provides keys from the user keystore*

## com/haf/client/model

* ContactInfo.java — *Data model for a contact*

## com/haf/client/models

* MessageType.java — *Enum defining the type of a message (TEXT, IMAGE, FILE)*
* MessageVM.java — *ViewModel representation of a single message*

## com/haf/client/network

* DefaultMessageReceiver.java — *Default implementation for receiving messages*
* DefaultMessageSender.java — *Default implementation for sending messages*
* MessageReceiver.java — *Interface for receiving messages*
* MessageSender.java — *Interface for sending messages*

* WebSocketAdapter.java — *Adapter for WebSocket connections*

## com/haf/client/utils

* MessageBubbleFactory.java — *Factory for creating chat bubble UI components*
* SslContextUtils.java — *Utilities for configuring SSL/TLS contexts*
* UiConstants.java — *Constants for UI*
* ViewRouter.java — *Handles view routing*
* WindowResizeHelper.java — *Utility to add resize handles to undecorated windows*

## com/haf/client/viewmodels

* LoginViewModel.java — *ViewModel for login screen*
* MessageViewModel.java — *ViewModel for handling message UI logic*
* RegisterViewModel.java — *ViewModel for registration screen*
* SplashViewModel.java — *ViewModel for splash screen*

---

# **server/src/main/java**

## module-info.java

*Module descriptor for the server module*

## com/haf/server/config

* ServerConfig.java — *Configuration class for the server*

## com/haf/server/core

* Main.java — *Entry point for the server application*

## com/haf/server/db

* EnvelopeDAO.java — *Data Access Object for message envelopes*
* FileUploadDAO.java — *Data Access Object for encrypted file uploads*
* SessionDAO.java — *Data Access Object for user sessions*
* UserDAO.java — *Data Access Object for users*

## com/haf/server/handlers

* EncryptedMessageValidator.java — *Validates encrypted messages*

## com/haf/server/ingress

* HttpIngressServer.java — *Handles HTTP ingress traffic*
* WebSocketIngressServer.java — *Handles WebSocket ingress traffic*

## com/haf/server/metrics

* AuditLogger.java — *Logs security and operational events*
* MetricsRegistry.java — *Registry for server metrics*

## com/haf/server/router

* MailboxRouter.java — *Routes messages to the correct mailbox*
* QueuedEnvelope.java — *Represents a message envelope in the queue*
* RateLimiterService.java — *Service for rate limiting requests*

---

# **shared/src/main/java**

## module-info.java

*Module descriptor for the shared module*

## com/haf/shared/constants

* CryptoConstants.java — *Constants used in cryptography*
* MessageHeader.java — *Constants for message headers*

## com/haf/shared/crypto

* AadCodec.java — *Codec for Additional Authenticated Data*
* CryptoECC.java — *ECC encryption and decryption utilities*
* CryptoService.java — *Service for cryptographic operations*
* MessageDecryptor.java — *Handles message decryption*
* MessageEncryptor.java — *Handles message encryption*

## com/haf/shared/dto

* EncryptedFileDTO.java — *DTO for end-to-end encrypted files*
* EncryptedMessage.java — *DTO representing an encrypted message*
* KeyMetadata.java — *Metadata for cryptographic keys*
* LoginRequest.java — *DTO for login request*
* LoginResponse.java — *DTO for login response*
* RegisterRequest.java — *DTO for registration request*
* RegisterResponse.java — *DTO for registration response*

## com/haf/shared/exceptions

* KeyNotFoundException.java — *Exception thrown when a key is not found*
* MessageExpiredException.java — *Exception thrown when a message has expired*
* MessageTamperedException.java — *Exception thrown when a message is tampered with*
* MessageValidationException.java — *Exception thrown during message validation*

## com/haf/shared/keystore

* KeyProvider.java — *Interface for providing keys*
* KeystoreBootstrap.java — *Bootstraps the keystore*
* KeystoreRoot.java — *Represents the root of the keystore*
* KeystoreSealing.java — *Handles keystore sealing and unsealing*
* UserKeystore.java — *Represents a user's keystore*

## com/haf/shared/utils

* ClockProvider.java — *Interface for providing the current time*
* FilePerms.java — *Utilities for file permissions*
* FingerprintUtil.java — *Utilities for generating fingerprints*
* FixedClockProvider.java — *Clock provider with a fixed time*
* JsonCodec.java — *Utilities for JSON encoding and decoding*
* MessageValidator.java — *Validates messages*
* PemCodec.java — *Utilities for PEM encoding and decoding*
* EccKeyIO.java — *Input/Output utilities for ECC keys*
* SystemClockProvider.java — *Clock provider using the system clock*
