package com.smartfix.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="delivery_tasks")
public class DeliveryTask {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="recipient_id",nullable=false) private Long recipientId;
    @Column(nullable=false,length=20) private String channel;
    @Column(name="event_type",nullable=false,length=80) private String eventType;
    @Column(name="dedup_key",nullable=false,unique=true,length=200) private String dedupKey;
    @Column(nullable=false,length=150) private String title;
    @Column(nullable=false,length=1000) private String message;
    @Column(name="reference_id") private Long referenceId;
    @Column(nullable=false,length=20) private String status;
    @Column(nullable=false) private int attempts;
    @Column(name="next_attempt_at",nullable=false) private Instant nextAttemptAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="delivered_at") private Instant deliveredAt;
    @Column(name="last_error",length=100) private String lastError;
    protected DeliveryTask() {}
    public DeliveryTask(Long recipient,String channel,String type,String title,String message,Long reference,String key,Instant now) {
        Notification.create(recipient,type,title,message,reference,now,key);
        this.recipientId=recipient;this.channel=channel;this.eventType=type;this.title=title;
        this.message=message;this.referenceId=reference;this.dedupKey=key;this.status="PENDING";
        this.createdAt=now;this.nextAttemptAt=now;
    }
    public boolean ready(Instant now){return status.equals("PENDING")&&!nextAttemptAt.isAfter(now);}
    public void attempted(){attempts++;}
    public void delivered(Instant now){status="DELIVERED";deliveredAt=now;lastError=null;}
    public void skipped(){status="SKIPPED";lastError="NO_ACTIVE_EMAIL_CONTACT";}
    public void failed(String reason,int maxAttempts,Instant retryAt){lastError=reason;status=attempts>=maxAttempts?"FAILED":"PENDING";nextAttemptAt=retryAt;}
    public void retry(Instant now){status="PENDING";attempts=0;nextAttemptAt=now;lastError=null;}
    public Long getId(){return id;} public Long getRecipientId(){return recipientId;}
    public String getChannel(){return channel;} public String getEventType(){return eventType;}
    public String getDedupKey(){return dedupKey;} public String getTitle(){return title;}
    public String getMessage(){return message;} public Long getReferenceId(){return referenceId;}
    public String getStatus(){return status;} public int getAttempts(){return attempts;}
    public Instant getNextAttemptAt(){return nextAttemptAt;} public String getLastError(){return lastError;}
    public Summary summary(){return new Summary(id,recipientId,channel,eventType,status,attempts,nextAttemptAt,lastError);}
    public record Summary(Long id,Long recipientId,String channel,String eventType,String status,int attempts,Instant nextAttemptAt,String lastError){}
}
