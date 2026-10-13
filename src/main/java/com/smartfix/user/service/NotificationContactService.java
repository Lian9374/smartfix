package com.smartfix.user.service;
import com.smartfix.user.event.NotificationContactChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.smartfix.common.exception.*;
import com.smartfix.user.domain.*;
import com.smartfix.user.repository.UserRepository;
import jakarta.validation.Validator;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

@Service
public class NotificationContactService {
    private final UserRepository accounts;private final UserService users;private final ApplicationEventPublisher events;
    private final Validator validator;private final Clock clock;
    public NotificationContactService(UserRepository accounts,UserService users,ApplicationEventPublisher events,Validator validator,Clock clock){this.accounts=accounts;this.users=users;this.events=events;this.validator=validator;this.clock=clock;}
    public void requireAdmin(Long actor){
        if(actor==null)throw new AccessDeniedException("Administrator access required.");
        try{var u=users.getUserAccess(actor);if(u.role()!=Role.ADMINISTRATOR||u.accountStatus()!=AccountStatus.ACTIVE||u.passwordChangeRequired())throw new AccessDeniedException("Administrator access required.");}
        catch(ResourceNotFoundException missing){throw new AccessDeniedException("Administrator access required.");}
    }
    @Transactional(readOnly=true)
    public Optional<String> email(Long userId){return accounts.findById(userId).filter(u->u.getAccountStatus()==AccountStatus.ACTIVE).map(User::getNotificationEmail);}
    @Transactional(readOnly=true)
    public List<Long> activeAdministratorIds(){return accounts.notificationRecipientIds(Role.ADMINISTRATOR,AccountStatus.ACTIVE);}
    @Transactional
    public void update(Long actor,Long userId,String raw){
        requireAdmin(actor);var value=raw==null?"":raw.trim();
        if(value.length()>254||value.indexOf('\r')>=0||value.indexOf('\n')>=0||!validator.validate(new Address(value)).isEmpty())throw new InputValidationException("Provide a valid email address, or leave it blank to disable business email.");
        var user=accounts.findByIdForUpdate(userId).orElseThrow(()->new ResourceNotFoundException("Account not found"));
        user.setNotificationEmail(value.isEmpty()?null:value);
        events.publishEvent(new NotificationContactChangedEvent(actor,userId,!value.isEmpty(),clock.instant()));
    }
    @Transactional(readOnly=true)
    public Page<Contact> list(Long actor,int page){requireAdmin(actor);return accounts.findAll(PageRequest.of(Math.max(0,page),20,Sort.by("id"))).map(u->new Contact(u.getId(),u.getDisplayName(),u.getNotificationEmail()));}
    public record Contact(Long id,String displayName,String email){}
    private record Address(@jakarta.validation.constraints.Email String email){}
}
