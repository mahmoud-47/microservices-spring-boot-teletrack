package com.teletrack.notificationservice.unit.service;

import com.teletrack.notificationservice.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService Unit Tests")
class EmailServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock TemplateEngine templateEngine;
    @InjectMocks EmailService emailService;

    @Test
    @DisplayName("sendEmail — success: creates message and sends it")
    void sendEmail_Success() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>Hello</html>");

        emailService.sendEmail("recipient@example.com", "Test Subject", "test-template", new Context());

        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("sendEmail — MessagingException does not propagate")
    void sendEmail_MessagingException_DoesNotPropagate() throws Exception {
        MimeMessage badMessage = mock(MimeMessage.class);
        // MimeMessageHelper(multipart=true) calls message.setContent(MimeMultipart) in constructor
        doThrow(new MessagingException("SMTP error"))
                .when(badMessage).setContent(any(Multipart.class));
        when(mailSender.createMimeMessage()).thenReturn(badMessage);

        assertDoesNotThrow(() ->
                emailService.sendEmail("recipient@example.com", "Subject", "template", new Context()));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

}
