package com.marcosdaag.deployops.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class EmailAlertService {

    private static final Logger logger = LoggerFactory.getLogger(EmailAlertService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:alerts@deployops.io}")
    private String mailFrom;

    @Value("${app.dashboard.url:https://deployopsapp.vercel.app/}")
    private String dashboardUrl;

    @Async("mailTaskExecutor")
    public void sendIncidentOpenedAlert(String toEmail, String serviceName, String serviceUrl, LocalDateTime startedAt) {
        String formattedDate = startedAt != null ? startedAt.format(DATE_FORMATTER) : LocalDateTime.now().format(DATE_FORMATTER);
        String subject = String.format("🚨 [DeployOps Alerta] Tu servicio \"%s\" está caído", serviceName);

        String htmlContent = buildEmailTemplate(
                "CRÍTICO · SERVICIO CAÍDO",
                "#e25252",
                serviceName,
                serviceUrl,
                "Se detectaron 3 fallos consecutivos de respuesta HTTP en tu endpoint. Se abrió un nuevo incidente.",
                formattedDate,
                null
        );

        dispatchEmail(toEmail, subject, htmlContent);
    }

    @Async("mailTaskExecutor")
    public void sendIncidentResolvedAlert(String toEmail, String serviceName, String serviceUrl, long durationSeconds, LocalDateTime resolvedAt) {
        String formattedDate = resolvedAt != null ? resolvedAt.format(DATE_FORMATTER) : LocalDateTime.now().format(DATE_FORMATTER);
        String formattedDuration = formatDuration(durationSeconds);
        String subject = String.format("✅ [DeployOps Resuelto] Tu servicio \"%s\" se ha recuperado", serviceName);

        String htmlContent = buildEmailTemplate(
                "RESUELTO · RECUPERACIÓN CONFIRMADA",
                "#2eb87e",
                serviceName,
                serviceUrl,
                "El servicio respondió exitosamente dos comprobaciones consecutivas. El incidente ha sido cerrado.",
                formattedDate,
                formattedDuration
        );

        dispatchEmail(toEmail, subject, htmlContent);
    }

    @Async("mailTaskExecutor")
    public void sendTestAlert(String toEmail, String serviceName, String serviceUrl) {
        String formattedDate = LocalDateTime.now().format(DATE_FORMATTER);
        String subject = String.format("🔔 [DeployOps Prueba] Guardia activa para \"%s\"", serviceName);

        String htmlContent = buildEmailTemplate(
                "TEST DE GUARDIA ACTIVA",
                "#d4ab6a",
                serviceName,
                serviceUrl,
                "Esta es una prueba de conectividad de guardia activa. Si este servicio sufriera una caída, serás notificado inmediatamente aquí.",
                formattedDate,
                null
        );

        dispatchEmail(toEmail, subject, htmlContent);
    }

    private void dispatchEmail(String toEmail, String subject, String htmlContent) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        if (!mailEnabled || mailSender == null) {
            logger.info("Guardia activa [MODO SIMULACION]: Alerta '{}' dirigida a {} (mail.enabled={})", subject, toEmail, mailEnabled);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Alerta de guardia activa enviada exitosamente a {}", toEmail);
        } catch (Exception e) {
            logger.error("Error al enviar alerta de guardia activa a {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildEmailTemplate(
            String badgeText,
            String badgeColor,
            String serviceName,
            String serviceUrl,
            String description,
            String dateText,
            String durationText
    ) {
        StringBuilder durationRow = new StringBuilder();
        if (durationText != null) {
            durationRow.append("<tr>")
                    .append("<td style=\"padding: 8px 0; color: #a8a69e; font-size: 13px;\">Tiempo inactivo:</td>")
                    .append("<td style=\"padding: 8px 0; color: #f3f0e8; font-size: 13px; font-weight: 600; text-align: right;\">")
                    .append(durationText)
                    .append("</td>")
                    .append("</tr>");
        }

        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin: 0; padding: 24px; background-color: #0b0c0b; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #f3f0e8;">
                  <table align="center" border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width: 560px; background-color: #121413; border: 1px solid rgba(243, 240, 232, 0.12); border-radius: 12px; overflow: hidden; margin: 0 auto;">
                    <tr>
                      <td style="padding: 24px 28px 16px; border-bottom: 1px solid rgba(243, 240, 232, 0.08);">
                        <table width="100%%">
                          <tr>
                            <td>
                              <span style="font-size: 18px; font-weight: 700; letter-spacing: -0.02em; color: #f3f0e8;">DeployOps</span>
                              <span style="display: block; font-size: 11px; color: #706e67; margin-top: 2px;">Observabilidad Activa</span>
                            </td>
                            <td align="right">
                              <span style="display: inline-block; padding: 4px 10px; border-radius: 4px; font-size: 11px; font-weight: 700; letter-spacing: 0.05em; text-transform: uppercase; background-color: %s; color: #ffffff;">
                                %s
                              </span>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding: 28px;">
                        <h2 style="margin: 0 0 10px; font-size: 20px; font-weight: 600; color: #f3f0e8;">
                          %s
                        </h2>
                        <p style="margin: 0 0 20px; font-size: 14px; line-height: 1.5; color: #a8a69e;">
                          %s
                        </p>
                        <table width="100%%" style="background-color: #171a18; border: 1px solid rgba(243, 240, 232, 0.08); border-radius: 8px; padding: 14px 18px; margin-bottom: 24px;">
                          <tr>
                            <td style="padding: 8px 0; color: #a8a69e; font-size: 13px;">URL Monitoreada:</td>
                            <td style="padding: 8px 0; color: #d4ab6a; font-size: 13px; font-weight: 500; text-align: right; word-break: break-all;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding: 8px 0; color: #a8a69e; font-size: 13px;">Fecha y hora:</td>
                            <td style="padding: 8px 0; color: #f3f0e8; font-size: 13px; text-align: right;">
                              %s
                            </td>
                          </tr>
                          %s
                        </table>
                        <div style="text-align: center;">
                          <a href="%s" style="display: inline-block; padding: 12px 28px; background-color: #f3f0e8; color: #0b0c0b; text-decoration: none; font-weight: 600; font-size: 13px; border-radius: 6px;">
                            Abrir Panel de Control &rarr;
                          </a>
                        </div>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding: 16px 28px; background-color: #0e100f; border-top: 1px solid rgba(243, 240, 232, 0.08); font-size: 11px; color: #706e67; text-align: center;">
                        Notificación automática enviada por el motor de guardia de DeployOps.
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                badgeColor,
                badgeText,
                serviceName,
                description,
                serviceUrl,
                dateText,
                durationRow.toString(),
                dashboardUrl
        );
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " seg";
        }
        long minutes = seconds / 60;
        long remainingSec = seconds % 60;
        if (minutes < 60) {
            return String.format("%dm %02ds", minutes, remainingSec);
        }
        long hours = minutes / 60;
        long remainingMin = minutes % 60;
        return String.format("%dh %02dm", hours, remainingMin);
    }
}
