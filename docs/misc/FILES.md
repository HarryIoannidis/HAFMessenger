# FILES

## client/src/main/java

* module-info.java — *Module descriptor for the client module*

## com/haf/client/controllers

* ChatController.java — *Controller for the chat view*
* ContactCell.java — *Custom ListCell for displaying contacts*
* LoginController.java — *Controller for login view*
* MainContentLoader.java — *Loads and manages main content area*
* MainController.java — *Controller for the main application shell*
* PreviewController.java — *Controller for file/image preview*
* ProfileController.java — *Controller for user profile view*
* RegisterController.java — *Controller for registration view*
* SearchContactActions.java — *Handles search contact action events*
* SearchController.java — *Controller for the search view*
* SplashController.java — *Controller for splash screen*

## com/haf/client/core

* ChatSession.java — *Container for the active chat session context*
* ClientApp.java — *Main client application class*
* CurrentUserSession.java — *Stores the currently authenticated user's state*
* Launcher.java — *Application launcher*
* NetworkSession.java — *Container for network sessions*

## com/haf/client/exceptions

* HttpCommunicationException.java — *Exception thrown on HTTP communication failure*
* RegistrationFlowException.java — *Exception thrown on registration flow errors*
* SslConfigurationException.java — *Exception thrown on SSL/TLS setup errors*

## com/haf/client/crypto

* UserKeystoreKeyProvider.java — *Provides keys from the user keystore*

## com/haf/client/models

* ContactInfo.java — *Data model for a contact*
* MessageType.java — *Enum defining the type of a message (TEXT, IMAGE, FILE)*
* MessageVM.java — *ViewModel representation of a single message*
* UserProfileInfo.java — *Data model for user profile information*

## com/haf/client/network

* DefaultMessageReceiver.java — *Default implementation for receiving messages*
* DefaultMessageSender.java — *Default implementation for sending messages*
* MessageReceiver.java — *Interface for receiving messages*
* MessageSender.java — *Interface for sending messages*
* WebSocketAdapter.java — *Adapter for WebSocket connections*

## com/haf/client/utils

* ContextMenuBuilder.java — *Builds context menus for UI components*
* ImageSaveSupport.java — *Utility for saving images from the chat*
* MessageBubbleFactory.java — *Factory for creating chat bubble UI components*
* SslContextUtils.java — *Utilities for configuring SSL/TLS contexts*
* UiConstants.java — *Constants for UI*
* ViewRouter.java — *Handles view routing*
* WindowResizeHelper.java — *Utility to add resize handles to undecorated windows*

## com/haf/client/viewmodels

* ChatViewModel.java — *ViewModel for the chat screen*
* LoginViewModel.java — *ViewModel for login screen*
* MainViewModel.java — *ViewModel for the main application shell*
* MessageViewModel.java — *ViewModel for handling message UI logic*
* RegisterViewModel.java — *ViewModel for registration screen*
* SearchViewModel.java — *ViewModel for the search screen*
* SplashViewModel.java — *ViewModel for splash screen*

---

## server/src/main/java

* module-info.java — *Module descriptor for the server module*

## com/haf/server/config

* ServerConfig.java — *Configuration class for the server*

## com/haf/server/core

* Main.java — *Entry point for the server application*

## com/haf/server/db

* AttachmentDAO.java — *Data Access Object for encrypted file attachment chunks*
* ContactDAO.java — *Data Access Object for user contacts*
* EnvelopeDAO.java — *Data Access Object for message envelopes*
* FileUploadDAO.java — *Data Access Object for encrypted file uploads*
* SessionDAO.java — *Data Access Object for user sessions*
* UserDAO.java — *Data Access Object for users*

## com/haf/server/exceptions

* ConfigurationException.java — *Exception used for configuration errors*
* DatabaseOperationException.java — *Exception used for database errors*
* RateLimitException.java — *Exception thrown when rate limit is exceeded*
* StartupException.java — *Exception used when startup fails*

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

## shared/src/main/java

* module-info.java — *Module descriptor for the shared module*

## com/haf/shared/constants

* AttachmentConstants.java — *Constants for file attachment operations*
* CryptoConstants.java — *Constants used in cryptography*
* MessageHeader.java — *Constants for message headers*

## com/haf/shared/crypto

* AadCodec.java — *Codec for Additional Authenticated Data*
* CryptoECC.java — *ECC encryption and decryption utilities*
* CryptoService.java — *Service for cryptographic operations*
* MessageDecryptor.java — *Handles message decryption*
* MessageEncryptor.java — *Handles message encryption*

## com/haf/shared/dto

* AttachmentInlinePayload.java — *DTO for inline attachment payload within a message*
* AttachmentReferencePayload.java — *DTO for attachment reference payload pointing to a stored file*
* EncryptedFileDTO.java — *DTO for end-to-end encrypted files*
* EncryptedMessage.java — *DTO representing an encrypted message*
* KeyMetadata.java — *Metadata for cryptographic keys*
* UserSearchResultDTO.java — *DTO containing single user info in search results*

## com/haf/shared/requests

* AddContactRequest.java — *Request DTO for adding a user to contacts*
* AttachmentBindRequest.java — *Request DTO for binding an attachment to a message*
* AttachmentChunkRequest.java — *Request DTO for uploading an attachment chunk*
* AttachmentCompleteRequest.java — *Request DTO for completing a chunked upload*
* AttachmentInitRequest.java — *Request DTO for initiating a chunked upload*
* LoginRequest.java — *Request DTO for login*
* RegisterRequest.java — *Request DTO for registration*

## com/haf/shared/responses

* AttachmentBindResponse.java — *Response DTO after binding an attachment to a message*
* AttachmentChunkResponse.java — *Response DTO after uploading an attachment chunk*
* AttachmentCompleteResponse.java — *Response DTO after completing a chunked upload*
* AttachmentDownloadResponse.java — *Response DTO for downloading an attachment*
* AttachmentInitResponse.java — *Response DTO after initiating a chunked upload*
* ContactsResponse.java — *Response DTO listing user contacts*
* LoginResponse.java — *Response DTO for login*
* MessagingPolicyResponse.java — *Response DTO for the applicable messaging policy*
* PublicKeyResponse.java — *Response DTO containing a user's public key*
* RegisterResponse.java — *Response DTO for registration*
* UserSearchResponse.java — *Response DTO containing user search results*

## com/haf/shared/exceptions

* CryptoOperationException.java — *Exception thrown on general crypto failure*
* JsonCodecException.java — *Exception thrown on JSON mapping errors*
* KeyNotFoundException.java — *Exception thrown when a key is not found*
* KeystoreOperationException.java — *Exception thrown on keystore errors*
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
* EccKeyIO.java — *Input/Output utilities for ECC keys*
* FilePerms.java — *Utilities for file permissions*
* FingerprintUtil.java — *Utilities for generating fingerprints*
* FixedClockProvider.java — *Clock provider with a fixed time*
* JsonCodec.java — *Utilities for JSON encoding and decoding*
* MessageValidator.java — *Validates messages*
* PemCodec.java — *Utilities for PEM encoding and decoding*
* SystemClockProvider.java — *Clock provider using the system clock*
