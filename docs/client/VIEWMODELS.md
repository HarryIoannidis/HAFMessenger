# VIEWMODELS

## Purpose

Summarize the implemented client ViewModel layer and ownership boundaries.

## Current Implementation

- Authentication/bootstrap: `LoginViewModel`, `RegisterViewModel`, `SplashViewModel`.
- Main-shell state: `MainViewModel`, `SearchViewModel`, `SearchSortViewModel`.
- Messaging state: `MessagesViewModel`, `ChatViewModel`.
- ViewModels expose JavaFX properties and keep network/business logic out of controllers.

## Key Types/Interfaces

- `client.viewmodels.LoginViewModel`
- `client.viewmodels.RegisterViewModel`
- `client.viewmodels.SplashViewModel`
- `client.viewmodels.MainViewModel`
- `client.viewmodels.SearchViewModel`
- `client.viewmodels.SearchSortViewModel`
- `client.viewmodels.MessagesViewModel`
- `client.viewmodels.ChatViewModel`

## Flow

1. Controllers bind to ViewModel properties and command handlers.
2. ViewModels call services/network interfaces for async work.
3. Property/list updates drive UI refresh and status labels.
4. Runtime recoverable issues are surfaced to UI via callbacks/listeners.

## Error/Security Notes

- ViewModels should emit user-safe error strings, not raw internal traces.
- Messaging ViewModels preserve validation/decrypt boundaries from shared/server contracts.

## Related Files

- `client/src/main/java/com/haf/client/viewmodels`
- `client/src/main/java/com/haf/client/controllers`
- `docs/client/scenes`
