package vn.glassliving.room.dto;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.ServletRequestDataBinder;

import static org.assertj.core.api.Assertions.assertThat;

class RoomFormBindingTest {

    @Test
    void checkedInheritTariffBindsToTrue() {
        RoomForm form = new RoomForm();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("_inheritTariff", "on");
        request.addParameter("inheritTariff", "true");

        new ServletRequestDataBinder(form).bind(request);

        assertThat(form.isInheritTariff()).isTrue();
    }

    @Test
    void uncheckedInheritTariffBindsToFalse() {
        RoomForm form = new RoomForm();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("_inheritTariff", "on");

        new ServletRequestDataBinder(form).bind(request);

        assertThat(form.isInheritTariff()).isFalse();
    }
}
