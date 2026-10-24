package com.sywater.ms_iam.infrastructure.notification;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.sywater.ms_iam.application.port.out.NotificationSender;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.infrastructure.config.IamProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Component
public class SmtpNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpNotificationSender.class);

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO")).withZone(ZoneId.of("America/Bogota"));

    private final JavaMailSender mail;
    private final IamProperties properties;
    private final String appUrl;

    public SmtpNotificationSender(JavaMailSender mail, IamProperties properties,
                                  @Value("${iam.mail.app-url:}") String appUrl) {
        this.mail = mail;
        this.properties = properties;
        this.appUrl = appUrl == null ? "" : appUrl.replaceAll("/+$", "");
    }

    @Override
    public void sendVerificationCode(Email to, String firstName, String code, Duration validFor) {
        send(to, "Tu código de verificación: " + code,
                "Confirma tu correo",
                "Hola " + HtmlUtils.htmlEscape(firstName) + ", usa este código para activar tu cuenta de Cuida Tu Agua:",
                code, "El código vence en " + humanize(validFor) + ". Si no creaste esta cuenta, ignora este correo.");
    }

    @Override
    public void sendPasswordResetCode(Email to, String firstName, String code, Duration validFor) {
        send(to, "Código para restablecer tu contraseña: " + code,
                "Restablece tu contraseña",
                "Hola " + HtmlUtils.htmlEscape(firstName) + ", recibimos una solicitud para cambiar tu contraseña. Tu código es:",
                code, "Vence en " + humanize(validFor) + " y solo sirve una vez. Si no lo pediste, ignora este correo: tu contraseña sigue igual.");
    }

    @Override
    public void sendPasswordChanged(Email to, String firstName) {
        send(to, "Tu contraseña cambió",
                "Tu contraseña cambió",
                "Hola " + HtmlUtils.htmlEscape(firstName) + ", la contraseña de tu cuenta de Cuida Tu Agua se acaba de cambiar.",
                null, "Si no fuiste tú, restablécela ahora desde la app con \"¿Olvidaste tu contraseña?\".");
    }

    @Override
    public void sendActionCode(Email to, String firstName, String action, String code, Duration validFor) {
        send(to, "Código para " + action + ": " + code,
                "Confirma esta acción",
                "Hola " + HtmlUtils.htmlEscape(firstName) + ", alguien pidió <b>" + HtmlUtils.htmlEscape(action)
                        + "</b> desde tu cuenta de Cuida Tu Agua. Para confirmarlo, escribe este código en la app:",
                code, "Vence en " + humanize(validFor) + " y solo sirve una vez. Si no fuiste tú, NO lo compartas "
                        + "y cambia tu contraseña: alguien podría estar usando tu cuenta.");
    }

    @Override
    public void sendCollaboratorInvitation(Email to, String placeName, String invitedByName, List<String> permissions,
                                           Instant expiresAt) {
        // Names come from other users: one line only, so they cannot add headers to the e-mail
        String plainWho = invitedByName == null || invitedByName.isBlank() ? "Alguien" : oneLine(invitedByName);
        String who = HtmlUtils.htmlEscape(plainWho);
        String what = permissions.stream().map(SmtpNotificationSender::describe).collect(Collectors.joining(", "));
        String link = appUrl.isBlank() ? null : appUrl + "/invitations";
        String how = link == null
                ? "Abre la app con esta misma dirección de correo y acepta la invitación desde tus invitaciones."
                : "Toca el botón, entra con esta misma dirección de correo y acepta la invitación.";
        send(to, plainWho + " te invitó a colaborar en \"" + oneLine(placeName) + "\"",
                "Te invitaron a colaborar",
                who + " te invitó a colaborar en el lugar <b>" + HtmlUtils.htmlEscape(placeName) + "</b> de Cuida Tu Agua. "
                        + "Podrás: " + HtmlUtils.htmlEscape(what) + ". " + how,
                null, "La invitación vence el " + DATE.format(expiresAt) + ". Si no conoces a quien te invita, ignora este correo.",
                "Ver invitación", link);
    }

    private static String oneLine(String text) {
        return text.replaceAll("[\\p{Cntrl}]+", " ").trim();
    }

    private static String describe(String permission) {
        return switch (permission) {
            case "VIEW_CONSUMPTION" -> "ver el consumo de agua";
            case "VIEW_DEVICE_STATUS" -> "ver el estado del dispositivo";
            case "CONTROL_VALVE" -> "abrir y cerrar la válvula";
            case "VIEW_ALERTS" -> "recibir y ver las alertas";
            case "VIEW_BILLS" -> "ver las tarifas y el costo";
            default -> permission.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private void send(Email to, String subject, String title, String intro, String code, String footer) {
        send(to, subject, title, intro, code, footer, null, null);
    }

    private void send(Email to, String subject, String title, String intro, String code, String footer,
                      String buttonLabel, String buttonUrl) {
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mail().from());
            helper.setTo(to.value());
            helper.setSubject(subject);
            helper.setText(html(title, intro, code, footer, buttonLabel, buttonUrl), true);
            mail.send(message);
        } catch (MailException | MessagingException e) {
            log.warn("Could not send '{}' to {}: {}", title, to.masked(), e.getMessage());
        }
    }

    private static String html(String title, String intro, String code, String footer, String buttonLabel, String buttonUrl) {
        String codeBlock = code == null ? "" :
                "<p style=\"font-size:32px;letter-spacing:8px;font-weight:bold;color:#023E8A;"
                        + "font-family:monospace;margin:24px 0\">" + code + "</p>";
        String buttonBlock = buttonUrl == null ? "" :
                "<p style=\"margin:24px 0\"><a href=\"" + HtmlUtils.htmlEscape(buttonUrl) + "\" style=\"background:#0096C7;"
                        + "color:#FFFFFF;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;"
                        + "display:inline-block\">" + HtmlUtils.htmlEscape(buttonLabel) + "</a></p>"
                        + "<p style=\"color:#6B7280;font-size:12px\">Si el botón no funciona, copia este enlace: "
                        + HtmlUtils.htmlEscape(buttonUrl) + "</p>";
        return "<div style=\"font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#0B5FA5\">"
                + "<h2 style=\"color:#0096C7\">" + title + "</h2>"
                + "<p>" + intro + "</p>" + codeBlock + buttonBlock
                + "<p style=\"color:#6B7280;font-size:13px\">" + footer + "</p>"
                + "<p style=\"color:#6B7280;font-size:12px\">— Cuida Tu Agua</p></div>";
    }

    private static String humanize(Duration d) {
        if (d.toHours() >= 1 && d.toMinutesPart() == 0) return d.toHours() + (d.toHours() == 1 ? " hora" : " horas");
        return d.toMinutes() + " minutos";
    }
}
