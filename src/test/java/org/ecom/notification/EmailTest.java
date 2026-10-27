package org.ecom.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void subjectCannotCarryExtraHeaders() {
        var email = new Email(List.of("a@example.test"), "Audit\r\nBcc: victim@example.test", "body", null);

        assertThat(email.subject()).isEqualTo("Audit Bcc: victim@example.test");
    }

}
