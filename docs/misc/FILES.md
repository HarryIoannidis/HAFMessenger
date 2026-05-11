# FILES

## Purpose

Index the main source layout and notable classes without duplicating generated/build output.

## Current Implementation

- Top-level runtime modules:
  - `client`
  - `server`
  - `shared`
- Packaging scripts live under:
  - `scripts`
- Each module uses `src/main/java` for production code and `src/test/java` for tests.

## Key Types/Interfaces

- Client notable classes:
  - Controllers: `MainController`, `ChatController`, `SearchController`, `SettingsController`, `ContactCellController`
  - ViewModels: `MainViewModel`, `MessagesViewModel`, `ChatViewModel`, `SearchViewModel`
  - Network: `AuthHttpClient`, `DefaultMessageSender`, `DefaultMessageReceiver`, `MessageSender`, `MessageReceiver`, `RealtimeClientTransport`, `RealtimeTransport`
  - Services: `AttachmentImageOptimizer`, `DefaultLoginService`, `DefaultRegistrationService`, `DefaultMainSessionService`, `DefaultTokenRefreshService`, `TokenRefreshService`, `DesktopNotificationService`
  - Builders: `ContextMenuBuilder`, `MessageBubbleFactory`, `PopupMessageBuilder`, `SettingsRowBuilder`
  - Security: `RememberedCredentialsStore`, `SecurePasswordVault`, `WindowsPasswordManager`, `MacOsKeychainPasswordVault`, `LinuxSecretToolPasswordVault`
- Server notable classes:
  - `Main`, `ServerConfig`
  - `HttpIngressServer`, `RealtimeWebSocketServer`, `MessageIngressService`
  - `MailboxRouter`, `RateLimiterService`
  - `MetricsRegistry`, `AuditLogger`, `EncryptedMessageValidator`, `JwtTokenService`
  - DAOs/Entities in `server.db`
- Shared notable classes:
  - `EncryptedMessage`, `EncryptedFile`, attachment payload DTOs, request/response DTOs including token refresh DTOs
  - `MessageValidator`, `JsonCodec`, `MessageEncryptor`, `MessageDecryptor`, `AttachmentPayloadCodec`
  - `KeyProvider`, `UserKeystore`, `KeystoreBootstrap`

## Flow

1. Client UI and ViewModel layers produce service/network calls.
2. Server ingress and router layers enforce policy and persistence.
3. Shared module supplies type-safe contracts and common crypto/validation.

## Error/Security Notes

- This file documents source layout only; build artifacts under `target/` are excluded.
- Keep class names synchronized with source tree on refactors (for example use `MessagesViewModel` consistently).

## Full File Inventory

- Client files (`src/main/java`):
  - `module-info.java`
  - Builders: `ContextMenuBuilder.java`, `MessageBubbleFactory.java`, `PopupMessageBuilder.java`, `SettingsRowBuilder.java`
  - Controllers: `ChatController.java`, `ContactCellController.java`, `LoginController.java`, `MainContentLoader.java`, `MainController.java`, `PopupMessageController.java`, `PreviewController.java`, `ProfileController.java`, `RegisterController.java`, `SearchController.java`, `SearchFilterController.java`, `SettingsController.java`, `SplashController.java`
  - Core: `AuthSessionState.java`, `ChatSession.java`, `ClientApp.java`, `CurrentUserSession.java`, `Launcher.java`, `NetworkSession.java`
  - Crypto: `UserKeystoreKeyProvider.java`
  - Exceptions: `ClientConfigurationException.java`, `HttpCommunicationException.java`, `RegistrationFlowException.java`, `SslConfigurationException.java`, `UiDispatchException.java`
  - Models: `ContactInfo.java`, `MessageType.java`, `MessageVM.java`, `SettingsMenuItem.java`, `UserProfileInfo.java`
  - Network: `DefaultMessageReceiver.java`, `DefaultMessageSender.java`, `MessageReceiver.java`, `MessageSender.java`, `AuthHttpClient.java`, `RealtimeClientTransport.java`, `RealtimeTransport.java`
  - Security: `LinuxSecretToolPasswordVault.java`, `MacOsKeychainPasswordVault.java`, `RememberedCredentialsStore.java`, `SecurePasswordVault.java`, `UnsupportedPasswordVault.java`, `WindowsPasswordManager.java`
  - Services: `AttachmentImageOptimizer.java`, `ChatAttachmentService.java`, `DefaultChatAttachmentService.java`, `DefaultLoginService.java`, `DefaultMainSessionService.java`, `DefaultRegistrationService.java`, `DefaultTokenRefreshService.java`, `DesktopNotificationService.java`, `LoginService.java`, `MainSessionService.java`, `RegistrationService.java`, `TokenRefreshService.java`
  - Utils: `ClientRuntimeConfig.java`, `ClientSettings.java`, `ImageSaveSupport.java`, `PopupMessageSpec.java`, `RankIconResolver.java`, `RuntimeIssue.java`, `RuntimeIssuePopupGate.java`, `SslContextUtils.java`, `UiConstants.java`, `ViewRouter.java`, `WindowResizeHelper.java`
  - ViewModels: `ChatViewModel.java`, `LoginViewModel.java`, `MainViewModel.java`, `MessagesViewModel.java`, `RegisterViewModel.java`, `SearchSortViewModel.java`, `SearchViewModel.java`, `SplashViewModel.java`
- Client files (`src/test/java`):
  - Controllers: `ChatControllerTest.java`, `ContactCellTest.java`, `LoginControllerTest.java`, `MainContentLoaderTest.java`, `MainControllerTest.java`, `PopupMessageControllerTest.java`, `PreviewControllerTest.java`, `RegisterControllerTest.java`, `SearchControllerTest.java`, `SearchFilterUiTest.java`, `SettingsControllerRememberCredentialsTest.java`, `SettingsControllerTest.java`, `SplashControllerTest.java`
  - Crypto: `UserKeystoreKeyProviderTest.java`
  - Network: `AuthHttpClientTest.java`, `MessageReceiverTest.java`, `MessageSenderTest.java`
  - Security: `LinuxSecretToolPasswordVaultTest.java`, `MacOsKeychainPasswordVaultTest.java`, `RememberedCredentialsStoreTest.java`, `WindowsPasswordManagerTest.java`
  - Services: `DefaultChatAttachmentServiceTest.java`, `DefaultLoginServiceTest.java`, `DefaultMainSessionServiceTest.java`, `DefaultRegistrationServiceTest.java`
  - Utils: `ClientRuntimeConfigTest.java`, `ClientSettingsTest.java`, `ImageSaveSupportTest.java`, `MessageBubbleFactoryTest.java`, `PopupMessageBuilderTest.java`, `RuntimeIssuePopupGateTest.java`, `SettingsRowBuilderTest.java`
  - ViewModels: `ChatViewModelTest.java`, `LoginViewModelTest.java`, `MainViewModelTest.java`, `MessageViewModelAttachmentTest.java`, `MessageViewModelIncomingListenerTest.java`, `MessageViewModelPresenceTest.java`, `MessageViewModelRuntimeTest.java`, `RegisterViewModelTest.java`, `SearchViewModelTest.java`, `SplashViewModelTest.java`
  - Integration tests: `AadConsistencyIT.java`, `MessageSendReceiveIT.java`, `MultiUserKeystoreCollisionIT.java`

- Server files (`src/main/java`):
  - `module-info.java`
  - Config: `ServerConfig.java`
  - Core: `Main.java`
  - DB: `Attachment.java`, `Contact.java`, `Envelope.java`, `FileUpload.java`, `Session.java`, `User.java`
  - Exceptions: `ConfigurationException.java`, `DatabaseOperationException.java`, `RateLimitException.java`, `StartupException.java`
  - Handlers: `EncryptedMessageValidator.java`
  - Ingress: `HttpIngressServer.java`
  - Metrics: `AuditLogger.java`, `MetricsRegistry.java`
  - Realtime: `MessageIngressService.java`, `RealtimeWebSocketServer.java`
  - Router: `MailboxRouter.java`, `QueuedEnvelope.java`, `RateLimiterService.java`
  - Security: `JwtTokenService.java`
- Server files (`src/test/java`):
  - Integration tests: `EnvelopeIT.java`, `RealtimeWebSocketServerIT.java`
  - Config: `ServerConfigTest.java`
  - Core: `MainTest.java`
  - DB: `AttachmentTest.java`, `ContactTest.java`, `EnvelopeTest.java`, `FileUploadTest.java`, `SessionTest.java`, `UserTest.java`
  - Handlers: `EncryptedMessageValidatorTest.java`
  - Ingress: `HttpIngressServerTest.java`
  - Metrics: `AuditLoggerTest.java`, `MetricsRegistryTest.java`
  - Realtime: `MessageIngressServiceTest.java`, `RealtimeWebSocketServerTest.java`
  - Router: `MailboxRouterTest.java`, `QueuedEnvelopeTest.java`, `RateLimiterServiceTest.java`
- Shared files (`src/main/java`):
  - `module-info.java`
  - Constants: `AttachmentConstants.java`, `CryptoConstants.java`, `MessageHeader.java`
  - Crypto: `AadCodec.java`, `CryptoECC.java`, `CryptoService.java`, `MessageDecryptor.java`, `MessageEncryptor.java`, `MessageSignatureService.java`
  - DTO: `AttachmentInlinePayload.java`, `AttachmentReferencePayload.java`, `EncryptedFile.java`, `EncryptedMessage.java`, `KeyMetadata.java`, `UserSearchResult.java`
  - Exceptions: `CryptoOperationException.java`, `JsonCodecException.java`, `KeyNotFoundException.java`, `KeystoreOperationException.java`, `MessageDecryptionException.java`, `MessageExpiredException.java`, `MessageTamperedException.java`, `MessageValidationException.java`
  - Keystore: `KeyProvider.java`, `KeystoreBootstrap.java`, `KeystoreRoot.java`, `KeystoreSealing.java`, `UserKeystore.java`
  - Requests: `AddContactRequest.java`, `AttachmentBindRequest.java`, `AttachmentCompleteRequest.java`, `AttachmentInitRequest.java`, `LoginRequest.java`, `RefreshTokenRequest.java`, `RegisterRequest.java`
  - Responses: `AttachmentBindResponse.java`, `AttachmentChunkResponse.java`, `AttachmentCompleteResponse.java`, `AttachmentInitResponse.java`, `ContactsResponse.java`, `LoginResponse.java`, `MessagingPolicyResponse.java`, `PublicKeyResponse.java`, `RefreshTokenResponse.java`, `RegisterResponse.java`, `UserSearchResponse.java`
  - Utils: `AttachmentPayloadCodec.java`, `ClockProvider.java`, `EccKeyIO.java`, `FilePerms.java`, `FingerprintUtil.java`, `FixedClockProvider.java`, `JsonCodec.java`, `MessageValidator.java`, `PemCodec.java`, `SigningKeyIO.java`, `SystemClockProvider.java`
  - Websocket: `RealtimeEvent.java`, `RealtimeEventType.java`

- Shared files (`src/test/java`):
  - Integration tests: `KeystoreE2EIT.java`, `KeystorePermsE2EIT.java`, `KeystoreTamperIT.java`, `KeystoreWrongPassIT.java`
  - Constants: `AttachmentConstantsTest.java`, `CryptoConstantsTest.java`, `MessageHeaderTest.java`
  - Crypto: `AadBindingTest.java`, `AadCodecTest.java`, `CryptoECCTest.java`, `CryptoServiceTest.java`, `MessageDecryptorTest.java`, `MessageEncryptorTest.java`, `MessageFlowTest.java`
  - DTO: `EncryptedFileTest.java`, `EncryptedMessageTest.java`, `KeyMetadataTest.java`, `RegisterResponseTest.java`, `UserSearchResultTest.java`
  - Exceptions: `MessageExpiryTest.java`, `MessageTamperingTests.java`
  - Keystore: `KeyProviderTest.java`, `KeystoreBootstrapIdempotentTest.java`, `KeystoreBootstrapTest.java`, `KeystoreRootTest.java`, `KeystoreSealingTest.java`, `UserKeystoreTest.java`
  - Requests: `AddContactRequestTest.java`, `LoginRequestTest.java`
  - Responses: `PublicKeyResponseTest.java`
  - Utils: `AttachmentPayloadCodecTest.java`, `ClockProviderTest.java`, `EccKeyIOTest.java`, `FilePermsTest.java`, `FingerprintUtilTest.java`, `JsonCodecTest.java`, `MessageValidatorTest.java`, `PemCodecTest.java`

## Related Files

- `client/src/main/java`
- `server/src/main/java`
- `shared/src/main/java`
- `docs/client/REMEMBERED_CREDENTIALS_SECURITY.md`
- `docs/misc/STRUCTURE.md`
- `docs/misc/scripts.md`
- `scripts/package-linux-appimage.sh`
- `scripts/package-mac-app.sh`
- `scripts/package-windows-app.ps1`
