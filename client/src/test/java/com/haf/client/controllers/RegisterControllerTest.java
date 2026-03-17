package com.haf.client.controllers;

import com.haf.client.services.RegistrationService;
import com.haf.client.viewmodels.RegisterViewModel;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegisterControllerTest {

    @Test
    void constructor_rejects_null_service() {
        assertThrows(NullPointerException.class, () -> new RegisterController(null));
    }

    @Test
    void build_registration_command_maps_view_model_values() throws Exception {
        RegistrationService fakeService = command -> new RegistrationService.RegistrationResult.Failure("unused");
        RegisterController controller = new RegisterController(fakeService);

        RegisterViewModel viewModel = readField(controller, "viewModel", RegisterViewModel.class);
        File idPhoto = new File("/tmp/id-photo.jpg");
        File selfiePhoto = new File("/tmp/selfie-photo.jpg");

        viewModel.nameProperty().set("John Doe");
        viewModel.regNumProperty().set("RN123");
        viewModel.idNumProperty().set("ID987");
        viewModel.rankProperty().set("SMINIAS");
        viewModel.phoneNumProperty().set("6900000000");
        viewModel.emailProperty().set("john@haf.gr");
        viewModel.passwordProperty().set("pass123");
        viewModel.idPhotoFileProperty().set(idPhoto);
        viewModel.selfiePhotoFileProperty().set(selfiePhoto);

        RegistrationService.RegistrationCommand command = controller.buildRegistrationCommand();

        assertEquals("John Doe", command.name());
        assertEquals("RN123", command.regNum());
        assertEquals("ID987", command.idNum());
        assertEquals("SMINIAS", command.rank());
        assertEquals("6900000000", command.phone());
        assertEquals("john@haf.gr", command.email());
        assertEquals("pass123", command.password());
        assertEquals(idPhoto, command.idPhoto());
        assertEquals(selfiePhoto, command.selfiePhoto());
        assertSame(fakeService, readField(controller, "registrationService", RegistrationService.class));
    }

    private static <T> T readField(Object target, String fieldName, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(target);
        return type.cast(value);
    }
}
