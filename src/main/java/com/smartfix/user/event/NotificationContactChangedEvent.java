package com.smartfix.user.event;
import java.time.Instant;
public record NotificationContactChangedEvent(Long actorId,Long userId,boolean configured,Instant occurredAt){}
