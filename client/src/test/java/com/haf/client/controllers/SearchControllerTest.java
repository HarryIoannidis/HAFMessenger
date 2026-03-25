package com.haf.client.controllers;

import com.haf.client.viewmodels.SearchViewModel;
import com.haf.shared.dto.UserSearchResultDTO;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SearchControllerTest {

    @Test
    void toggle_contact_delegates_to_add_when_user_not_in_contacts() {
        SearchController controller = new SearchController();
        StubSearchContactActions actions = new StubSearchContactActions();
        actions.hasContact.set(false);
        controller.setContactActions(actions);

        controller.handleToggleContact(sampleUser());

        assertEquals(1, actions.addCalls.get());
        assertEquals(0, actions.removeCalls.get());
    }

    @Test
    void toggle_contact_routes_remove_through_confirmation_gate() {
        AtomicInteger addCalls = new AtomicInteger();
        AtomicInteger confirmRemoveCalls = new AtomicInteger();

        SearchController.applyContactToggleAction(
                SearchViewModel.ContactToggleAction.REMOVE_CONTACT,
                addCalls::incrementAndGet,
                confirmRemoveCalls::incrementAndGet);

        assertEquals(0, addCalls.get());
        assertEquals(1, confirmRemoveCalls.get());
    }

    @Test
    void toggle_contact_routes_add_immediately() {
        AtomicInteger addCalls = new AtomicInteger();
        AtomicInteger confirmRemoveCalls = new AtomicInteger();

        SearchController.applyContactToggleAction(
                SearchViewModel.ContactToggleAction.ADD_CONTACT,
                addCalls::incrementAndGet,
                confirmRemoveCalls::incrementAndGet);

        assertEquals(1, addCalls.get());
        assertEquals(0, confirmRemoveCalls.get());
    }

    @Test
    void start_chat_delegates_through_port() {
        SearchController controller = new SearchController();
        StubSearchContactActions actions = new StubSearchContactActions();
        controller.setContactActions(actions);

        controller.handleStartChat(sampleUser());

        assertEquals(1, actions.startChatCalls.get());
    }

    @Test
    void open_profile_delegates_through_port() {
        SearchController controller = new SearchController();
        StubSearchContactActions actions = new StubSearchContactActions();
        controller.setContactActions(actions);

        controller.handleOpenProfile(sampleUser());

        assertEquals(1, actions.openProfileCalls.get());
    }

    @Test
    void default_no_op_contact_actions_are_safe_when_not_explicitly_wired() {
        SearchController controller = new SearchController();

        assertDoesNotThrow(() -> controller.resolveToggleLabel("u-1"));
        assertDoesNotThrow(() -> controller.handleToggleContact(sampleUser()));
        assertDoesNotThrow(() -> controller.handleStartChat(sampleUser()));
        assertDoesNotThrow(() -> controller.handleOpenProfile(sampleUser()));
    }

    @Test
    void toggle_policy_methods_are_pure_and_deterministic() {
        SearchViewModel viewModel = new SearchViewModel((query, limit, cursor) -> "{}");

        SearchViewModel.ContactToggleAction addAction = viewModel.resolveContactToggleAction(false);
        SearchViewModel.ContactToggleAction removeAction = viewModel.resolveContactToggleAction(true);

        assertEquals(SearchViewModel.ContactToggleAction.ADD_CONTACT, addAction);
        assertEquals("Add contact", viewModel.resolveContactToggleLabel(addAction));
        assertEquals(SearchViewModel.ContactToggleAction.REMOVE_CONTACT, removeAction);
        assertEquals("Remove contact", viewModel.resolveContactToggleLabel(removeAction));
    }

    @Test
    void search_controller_has_no_direct_dependency_on_main_controller() {
        boolean mainControllerFieldFound = Arrays.stream(SearchController.class.getDeclaredFields())
                .anyMatch(field -> field.getType().equals(MainController.class));
        boolean mainControllerMethodTypeFound = Arrays.stream(SearchController.class.getDeclaredMethods())
                .anyMatch(method -> method.getReturnType().equals(MainController.class)
                        || Arrays.stream(method.getParameterTypes()).anyMatch(type -> type.equals(MainController.class)));

        assertFalse(mainControllerFieldFound);
        assertFalse(mainControllerMethodTypeFound);
    }

    private static UserSearchResultDTO sampleUser() {
        return new UserSearchResultDTO("u-1", "Jane Doe", "123", "jane@haf.gr", "SMINIAS", true);
    }

    private static final class StubSearchContactActions implements SearchController.ContactActions {
        private final AtomicBoolean hasContact = new AtomicBoolean(false);
        private final AtomicInteger addCalls = new AtomicInteger();
        private final AtomicInteger removeCalls = new AtomicInteger();
        private final AtomicInteger startChatCalls = new AtomicInteger();
        private final AtomicInteger openProfileCalls = new AtomicInteger();

        @Override
        public boolean hasContact(String userId) {
            return hasContact.get();
        }

        @Override
        public void addContact(UserSearchResultDTO result) {
            addCalls.incrementAndGet();
        }

        @Override
        public void removeContact(String userId) {
            removeCalls.incrementAndGet();
        }

        @Override
        public void startChatWith(UserSearchResultDTO result) {
            startChatCalls.incrementAndGet();
        }

        @Override
        public void openProfile(UserSearchResultDTO result) {
            openProfileCalls.incrementAndGet();
        }
    }
}
