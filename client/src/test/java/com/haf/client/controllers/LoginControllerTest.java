package com.haf.client.controllers;

import com.haf.client.security.RememberedCredentialsStore;
import com.haf.client.security.SecurePasswordVault;
import com.haf.client.services.LoginService;
import com.haf.client.viewmodels.LoginViewModel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginControllerTest {

    private static final String PREF_REMEMBER_ME = "remember_me";
    private static final String PREF_REMEMBERED_EMAIL = "remembered_email";

    private Preferences testNode;

    @AfterEach
    void cleanupPreferences() throws Exception {
        if (testNode != null) {
            testNode.removeNode();
            testNode.flush();
        }
    }

    @Test
    void constructor_rejects_null_service() {
        assertThrows(NullPointerException.class, () -> new LoginController(null));
    }

    @Test
    void build_login_command_maps_view_model_values() throws Exception {
        LoginService fakeService = failingLoginService();
        LoginController controller = new LoginController(fakeService);

        LoginViewModel viewModel = readField(controller, "viewModel", LoginViewModel.class);
        viewModel.setEmail("pilot@haf.gr");
        viewModel.setPassword("secret123");

        LoginService.LoginCommand command = controller.buildLoginCommand();

        assertEquals("pilot@haf.gr", command.email());
        assertEquals("secret123", command.password());
        assertSame(fakeService, readField(controller, "loginService", LoginService.class));
    }

    @Test
    void load_remembered_credentials_prefills_view_model_fields() throws Exception {
        LoginService fakeService = failingLoginService();
        Preferences prefs = newTestNode();
        prefs.putBoolean(PREF_REMEMBER_ME, true);
        prefs.put(PREF_REMEMBERED_EMAIL, "pilot@haf.gr");

        FakeVault vault = new FakeVault();
        vault.passwords.put("pilot@haf.gr", "secret123");
        RememberedCredentialsStore rememberedStore = new RememberedCredentialsStore(prefs, vault);
        LoginController controller = new LoginController(fakeService, rememberedStore);

        invokePrivateVoid(controller, "loadRememberedCredentials");

        LoginViewModel viewModel = readField(controller, "viewModel", LoginViewModel.class);
        assertEquals("pilot@haf.gr", viewModel.getEmail());
        assertEquals("secret123", viewModel.getPassword());
        assertTrue(viewModel.isRememberCredentials());
    }

    @Test
    void persist_remembered_credentials_uses_current_view_model_values() throws Exception {
        LoginService fakeService = failingLoginService();
        Preferences prefs = newTestNode();
        FakeVault vault = new FakeVault();
        RememberedCredentialsStore rememberedStore = new RememberedCredentialsStore(prefs, vault);
        LoginController controller = new LoginController(fakeService, rememberedStore);
        LoginViewModel viewModel = readField(controller, "viewModel", LoginViewModel.class);

        viewModel.setEmail("pilot@haf.gr");
        viewModel.setPassword("secret123");
        viewModel.rememberCredentialsProperty().set(true);

        invokePrivateVoid(controller, "persistRememberedCredentials");

        assertTrue(prefs.getBoolean(PREF_REMEMBER_ME, false));
        assertEquals("pilot@haf.gr", prefs.get(PREF_REMEMBERED_EMAIL, ""));
        assertEquals("secret123", vault.passwords.get("pilot@haf.gr"));
    }

    private static <T> T readField(Object target, String fieldName, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(target);
        return type.cast(value);
    }

    private static void invokePrivateVoid(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private Preferences newTestNode() {
        testNode = Preferences.userRoot().node("/com/haf/client/test/login-controller-" + UUID.randomUUID());
        return testNode;
    }

    private static LoginService failingLoginService() {
        return new LoginService() {
            @Override
            public LoginResult login(LoginCommand command) {
                return new LoginResult.Failure("unused");
            }

            @Override
            public LoginResult performKeyTakeover(LoginCommand command) {
                return new LoginResult.Failure("unused");
            }
        };
    }

    private static final class FakeVault implements SecurePasswordVault {

        private final Map<String, String> passwords = new HashMap<>();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean savePassword(String accountKey, String password) {
            passwords.put(accountKey, password);
            return true;
        }

        @Override
        public Optional<String> loadPassword(String accountKey) {
            return Optional.ofNullable(passwords.get(accountKey));
        }

        @Override
        public boolean deletePassword(String accountKey) {
            passwords.remove(accountKey);
            return true;
        }
    }
}
