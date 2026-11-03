package com.marcosdaag.deployops.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailAlertServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    private EmailAlertService emailAlertService;

    @BeforeEach
    void setUp() {
        emailAlertService = new EmailAlertService(mailSenderProvider);
        ReflectionTestUtils.setField(emailAlertService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailAlertService, "mailFrom", "alerts@deployops.io");
        ReflectionTestUtils.setField(emailAlertService, "dashboardUrl", "https://deployopsapp.vercel.app/");
    }

    @Test
    @DisplayName("Despacha alerta de incidente abierto cuando mailEnabled es true")
    void whenIncidentOpenedAndMailEnabled_shouldSendEmail() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        assertDoesNotThrow(() -> emailAlertService.sendIncidentOpenedAlert(
                "dev@example.com",
                "API Pagos",
                "https://api.pagos.com/health",
                LocalDateTime.now()
        ));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Despacha alerta de recuperacion con duracion de corte cuando mailEnabled es true")
    void whenIncidentResolvedAndMailEnabled_shouldSendEmail() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        assertDoesNotThrow(() -> emailAlertService.sendIncidentResolvedAlert(
                "dev@example.com",
                "API Pagos",
                "https://api.pagos.com/health",
                320L,
                LocalDateTime.now()
        ));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Despacha alerta de prueba cuando mailEnabled es true")
    void whenTestAlertAndMailEnabled_shouldSendEmail() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        assertDoesNotThrow(() -> emailAlertService.sendTestAlert(
                "dev@example.com",
                "API Pagos",
                "https://api.pagos.com/health"
        ));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Registra en log sin error cuando mailEnabled es false (Modo simulacion)")
    void whenMailDisabled_shouldNotSendEmail() {
        ReflectionTestUtils.setField(emailAlertService, "mailEnabled", false);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        assertDoesNotThrow(() -> emailAlertService.sendIncidentOpenedAlert(
                "dev@example.com",
                "API Pagos",
                "https://api.pagos.com/health",
                LocalDateTime.now()
        ));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Tolera ausencia de JavaMailSender sin lanzar excepcion")
    void whenMailSenderUnavailable_shouldNotFail() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        assertDoesNotThrow(() -> emailAlertService.sendIncidentOpenedAlert(
                "dev@example.com",
                "API Pagos",
                "https://api.pagos.com/health",
                LocalDateTime.now()
        ));
    }
}
