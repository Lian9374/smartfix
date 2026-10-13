package com.smartfix.sla.domain;
import com.smartfix.request.domain.UrgencyLevel;
import jakarta.persistence.*;
@Entity @Table(name="sla_policies",uniqueConstraints=@UniqueConstraint(columnNames={"urgency","phase"}))
public class SlaPolicy {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private UrgencyLevel urgency;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private SlaPhase phase;
    @Column(name="duration_seconds",nullable=false) private long durationSeconds;
    @Column(name="reminder_seconds",nullable=false) private long reminderSeconds;
    @Column(nullable=false) private boolean enabled;
    protected SlaPolicy(){}
    public SlaPolicy(UrgencyLevel urgency,SlaPhase phase){this.urgency=urgency;this.phase=phase;}
    public void configure(long duration,long reminder,boolean enabled){if(duration<1||duration>31536000||reminder<0||reminder>duration)throw new IllegalArgumentException("Invalid SLA policy");this.durationSeconds=duration;this.reminderSeconds=reminder;this.enabled=enabled;}
    public Long getId(){return id;}public UrgencyLevel getUrgency(){return urgency;}public SlaPhase getPhase(){return phase;}
    public long getDurationSeconds(){return durationSeconds;}public long getReminderSeconds(){return reminderSeconds;}public boolean isEnabled(){return enabled;}
}
