package kr.co.kumsungenc.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SmtpConfigurationServiceTest {

    @Test
    void exposesUsefulConfigurationWithoutExposingPassword() {
        SmtpConfigurationService service = new SmtpConfigurationService(
                "GMAIL",
                "smtp.gmail.com",
                587,
                "sender@example.com",
                "secret-app-password",
                "sender@example.com",
                "estimate@example.com",
                "b2b@example.com",
                "support@example.com",
                true,
                true,
                false);

        Map<String, Object> view = service.sanitizedView();

        assertThat(view)
                .containsEntry("provider", "GMAIL")
                .containsEntry("username", "se***@example.com")
                .containsEntry("passwordConfigured", true)
                .containsEntry("configured", true)
                .doesNotContainValue("secret-app-password");
    }

    @Test
    void reportsAuthenticatedSmtpWithoutPasswordAsIncomplete() {
        SmtpConfigurationService service = new SmtpConfigurationService(
                "CUSTOM",
                "smtp.company.com",
                587,
                "sender@company.com",
                "",
                "sender@company.com",
                "estimate@company.com",
                "b2b@company.com",
                "support@company.com",
                true,
                true,
                false);

        assertThat(service.sanitizedView()).containsEntry("configured", false);
    }

    @Test
    void detectsDaumAndHanmailSmtpProvider() {
        SmtpConfigurationService service = new SmtpConfigurationService(
                "AUTO",
                "smtp.daum.net",
                465,
                "sender@hanmail.com",
                "app-password",
                "sender@hanmail.com",
                "sender@hanmail.com",
                "sender@hanmail.com",
                "support@hanmail.com",
                true,
                false,
                true);

        assertThat(service.sanitizedView())
                .containsEntry("provider", "DAUM")
                .containsEntry("ssl", true)
                .containsEntry("configured", true)
                .doesNotContainValue("app-password");
    }
}
