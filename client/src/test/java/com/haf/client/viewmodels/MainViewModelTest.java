package com.haf.client.viewmodels;

import com.haf.client.models.ContactInfo;
import com.haf.shared.dto.ContactsResponse;
import com.haf.shared.dto.UserSearchResult;
import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MainViewModelTest {

    @Test
    void fetch_contacts_upserts_without_duplicates() {
        ContactsResponse first = ContactsResponse.success(List.of(
                new UserSearchResult("u-1", "Jane Doe", "100", "jane@haf.gr", "SMINIAS", false)));
        ContactsResponse second = ContactsResponse.success(List.of(
                new UserSearchResult("u-1", "Jane Updated", "100", "jane@haf.gr", "SMINIAS", true)));

        AtomicInteger calls = new AtomicInteger();
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture(
                        calls.incrementAndGet() == 1 ? JsonCodec.toJson(first) : JsonCodec.toJson(second))));

        viewModel.fetchContacts();
        awaitCondition(() -> viewModel.contactsProperty().size() == 1);
        assertEquals("Jane Doe", viewModel.contactsProperty().getFirst().name());
        assertEquals("Inactive", viewModel.contactsProperty().getFirst().activenessLabel());

        viewModel.fetchContacts();
        awaitCondition(() -> "Jane Updated".equals(viewModel.contactsProperty().getFirst().name()));

        assertEquals(1, viewModel.contactsProperty().size());
        assertEquals("Active", viewModel.contactsProperty().getFirst().activenessLabel());
    }

    @Test
    void add_contact_adds_locally_and_calls_gateway_once() {
        AtomicInteger addCalls = new AtomicInteger();
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}"),
                userId -> {
                    addCalls.incrementAndGet();
                    return CompletableFuture.completedFuture("{}");
                },
                userId -> CompletableFuture.completedFuture("{}")));

        viewModel.addContact("u-1", "Jane", "100");
        viewModel.addContact("u-1", "Jane", "100");

        assertTrue(viewModel.hasContact("u-1"));
        assertEquals(1, viewModel.contactsProperty().size());
        assertEquals(1, addCalls.get());
    }

    @Test
    void remove_contact_removes_locally_and_calls_gateway() {
        AtomicInteger removeCalls = new AtomicInteger();
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}"),
                userId -> CompletableFuture.completedFuture("{}"),
                userId -> {
                    removeCalls.incrementAndGet();
                    return CompletableFuture.completedFuture("{}");
                }));

        viewModel.ensureChatContact("u-1", "Jane", "100");
        assertTrue(viewModel.hasContact("u-1"));

        viewModel.removeContact("u-1");

        assertFalse(viewModel.hasContact("u-1"));
        assertEquals(1, removeCalls.get());
    }

    @Test
    void ensure_chat_contact_returns_existing_contact_without_duplicate() {
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}")));

        ContactInfo created = viewModel.ensureChatContact("u-1", "Jane", "100");
        ContactInfo second = viewModel.ensureChatContact("u-1", "Jane", "100");

        assertSame(created, second);
        assertEquals(1, viewModel.contactsProperty().size());
    }

    @Test
    void update_contact_presence_replaces_contact_state() {
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}")));

        viewModel.ensureChatContact("u-1", "Jane", "100");

        ContactInfo updated = viewModel.updateContactPresence("u-1", true);

        assertNotNull(updated);
        assertEquals("Active", updated.activenessLabel());
        assertEquals("#00b706", updated.activenessColor());
        assertEquals("Active", viewModel.contactsProperty().getFirst().activenessLabel());
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadlineNanos) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for async operation");
            }
        }
        fail("Timed out waiting for async operation");
    }

    private static final class StubContactsGateway implements MainViewModel.ContactsGateway {
        private final java.util.function.Supplier<CompletableFuture<String>> fetch;
        private final java.util.function.Function<String, CompletableFuture<String>> add;
        private final java.util.function.Function<String, CompletableFuture<String>> remove;

        private StubContactsGateway(java.util.function.Supplier<CompletableFuture<String>> fetch) {
            this(fetch, userId -> CompletableFuture.completedFuture("{}"),
                    userId -> CompletableFuture.completedFuture("{}"));
        }

        private StubContactsGateway(
                java.util.function.Supplier<CompletableFuture<String>> fetch,
                java.util.function.Function<String, CompletableFuture<String>> add,
                java.util.function.Function<String, CompletableFuture<String>> remove) {
            this.fetch = fetch;
            this.add = add;
            this.remove = remove;
        }

        @Override
        public CompletableFuture<String> fetchContacts() {
            return fetch.get();
        }

        @Override
        public CompletableFuture<String> addContact(String userId) {
            return add.apply(userId);
        }

        @Override
        public CompletableFuture<String> removeContact(String userId) {
            return remove.apply(userId);
        }
    }
}
