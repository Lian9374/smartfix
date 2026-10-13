package com.smartfix.notification.service;
import com.smartfix.notification.domain.DeliveryTask;
import com.smartfix.user.service.NotificationContactService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class EmailDeliveryService {
    private final JavaMailSender sender;private final NotificationContactService contacts;private final String from;
    public EmailDeliveryService(JavaMailSender sender,NotificationContactService contacts,@Value("${smartfix.notification.email.from:smartfix@localhost}") String from){this.sender=sender;this.contacts=contacts;this.from=from;}
    public boolean send(DeliveryTask task){
        var address=contacts.email(task.getRecipientId());if(address.isEmpty())return false;
        try{
            var mail=sender.createMimeMessage();var helper=new MimeMessageHelper(mail,"UTF-8");
            helper.setFrom(from);helper.setTo(address.get());helper.setSubject(task.getTitle());helper.setText(task.getMessage(),false);
            var hash=java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(task.getDedupKey().getBytes(StandardCharsets.UTF_8)));
            mail.saveChanges();mail.setHeader("Message-ID","<"+hash+"@smartfix.local>");sender.send(mail);return true;
        }catch(jakarta.mail.MessagingException|java.security.NoSuchAlgorithmException failure){throw new IllegalStateException("Email delivery failed",failure);}
    }
}
