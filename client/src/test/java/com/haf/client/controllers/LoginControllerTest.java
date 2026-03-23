package com.haf.client.controllers;

import com.haf.client.services.LoginService;
import com.haf.client.viewmodels.LoginViewModel;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginControllerTest {

    @Test
    void constructor_rejects_null_service() {
        assertThrows(NullPointerException.class, () -> new LoginController(null));
    }

    @Test
    void build_login_command_maps_view_model_values() throws Exception {
        LoginService fakeService = command -> new LoginService.LoginResult.Failure("unused");
        LoginController controller = new LoginController(fakeService);

        LoginViewModel viewModel = readField(controller, "viewModel", LoginViewModel.class);
        viewModel.setEmail("pilot@haf.gr");
        viewModel.setPassword("secret123");

        LoginService.LoginCommand command = controller.buildLoginCommand();

        assertEquals("pilot@haf.gr", command.email());
        assertEquals("secret123", command.password());
        assertSame(fakeService, readField(controller, "loginService", LoginService.class));
    }

    private static <T> T readField(Object target, String fieldName, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(target);
        return type.cast(value);
    }
}
