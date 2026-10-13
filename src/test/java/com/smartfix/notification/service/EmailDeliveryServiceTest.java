package com.smartfix.notification.service;
import com.smartfix.notification.domain.DeliveryTask;
import com.smartfix.user.service.NotificationContactService;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EmailDeliveryServiceTest {
    private DeliveryTask task(){return new DeliveryTask(1L,"EMAIL","TEST","Generic title","Generic body",null,"stable-email-key",Instant.EPOCH);}
    @Test void noAddressDoesNotUseUsernameAsDestination(){var sender=mock(JavaMailSender.class);var contacts=mock(NotificationContactService.class);when(contacts.email(1L)).thenReturn(Optional.empty());assertFalse(new EmailDeliveryService(sender,contacts,"smartfix@localhost").send(task()));verifyNoInteractions(sender);}
    @Test void mailHasStableIdentityAndPlainTextOnly()throws Exception{var sender=mock(JavaMailSender.class);var contacts=mock(NotificationContactService.class);when(contacts.email(1L)).thenReturn(Optional.of("fixture@example.test"));var first=new MimeMessage(Session.getInstance(new Properties()));var second=new MimeMessage(Session.getInstance(new Properties()));when(sender.createMimeMessage()).thenReturn(first,second);var service=new EmailDeliveryService(sender,contacts,"smartfix@localhost");assertTrue(service.send(task()));assertTrue(service.send(task()));assertEquals(first.getMessageID(),second.getMessageID());assertEquals("fixture@example.test",first.getAllRecipients()[0].toString());assertEquals("Generic body",first.getContent());verify(sender).send(first);verify(sender).send(second);}
    @Test void transportFailureIsExposedToBoundedQueueRetry(){var sender=mock(JavaMailSender.class);var contacts=mock(NotificationContactService.class);when(contacts.email(1L)).thenReturn(Optional.of("fixture@example.test"));when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));doThrow(new org.springframework.mail.MailSendException("synthetic")).when(sender).send(any(MimeMessage.class));assertThrows(org.springframework.mail.MailSendException.class,()->new EmailDeliveryService(sender,contacts,"smartfix@localhost").send(task()));}
}
