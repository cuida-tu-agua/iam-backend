package com.sywater.ms_iam.infrastructure.notification;

import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.infrastructure.config.IamProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Correos transaccionales: se prueba el contenido con un JavaMailSender simulado (no se envía nada). */
@ExtendWith(MockitoExtension.class)
class SmtpNotificationSenderTest {

    private static final Email TO = new Email("ana@correo.com");

    @Mock private JavaMailSender mail;

    private SmtpNotificationSender sender;

    @BeforeEach
    void setUp() {
        IamProperties properties = new IamProperties(null, null, null, null,
                new IamProperties.Mail("no-reply@cuidatuagua.test"), null);
        sender = new SmtpNotificationSender(mail, properties);
        when(mail.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("sendVerificationCode: asunto con el código y vigencia en horas")
    void givenVerificationCode_whenSend_thenSubjectHasCodeAndValidityInHours() throws Exception {
        // Act
        sender.sendVerificationCode(TO, "Ana", "123456", Duration.ofHours(24));

        // Assert
        MimeMessage message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("Tu código de verificación: 123456");
        assertThat(message.getFrom()[0].toString()).isEqualTo("no-reply@cuidatuagua.test");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("ana@correo.com");
        assertThat(message.getContent().toString()).contains("123456").contains("24 horas").contains("Hola Ana");
    }

    @Test
    @DisplayName("sendPasswordResetCode: vigencia en minutos")
    void givenResetCode_whenSend_thenValidityInMinutes() throws Exception {
        // Act
        sender.sendPasswordResetCode(TO, "Ana", "654321", Duration.ofMinutes(15));

        // Assert
        MimeMessage message = sentMessage();
        assertThat(message.getSubject()).contains("654321");
        assertThat(message.getContent().toString()).contains("15 minutos");
    }

    @Test
    @DisplayName("sendPasswordChanged: el aviso no lleva código")
    void givenPasswordChanged_whenSend_thenNoCodeBlock() throws Exception {
        // Act
        sender.sendPasswordChanged(TO, "Ana");

        // Assert
        MimeMessage message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("Tu contraseña cambió");
        assertThat(message.getContent().toString()).doesNotContain("letter-spacing");
    }

    @Test
    @DisplayName("sendActionCode: escapa el HTML del nombre y de la acción, y usa 1 hora en singular")
    void givenHtmlInNameAndAction_whenSendActionCode_thenEscapesIt() throws Exception {
        // Act
        sender.sendActionCode(TO, "<script>x</script>", "cerrar <b>válvula</b>", "111111", Duration.ofHours(1));

        // Assert
        String body = sentMessage().getContent().toString();
        assertThat(body).doesNotContain("<script>").contains("&lt;script&gt;");
        assertThat(body).contains("&lt;b&gt;válvula&lt;/b&gt;").contains("1 hora");
    }

    @Test
    @DisplayName("send: si el servidor de correo falla, no se propaga la excepción")
    void givenMailServerDown_whenSend_thenDoesNotThrow() {
        // Arrange
        doThrow(new MailSendException("SMTP caído")).when(mail).send(any(MimeMessage.class));

        // Act + Assert
        assertThatCode(() -> sender.sendVerificationCode(TO, "Ana", "123456", Duration.ofHours(24)))
                .doesNotThrowAnyException();
    }
}
