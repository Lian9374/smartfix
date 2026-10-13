package com.smartfix.sla.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="sla_states",uniqueConstraints=@UniqueConstraint(columnNames={"request_id","phase"}))
public class SlaState {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="request_id",nullable=false) private Long requestId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private SlaPhase phase;
    @Column(nullable=false) private int cycle;
    @Column(nullable=false,length=20) private String status;
    @Column(name="duration_seconds",nullable=false) private long durationSeconds;
    @Column(name="reminder_seconds",nullable=false) private long reminderSeconds;
    @Column(name="started_at",nullable=false) private Instant startedAt;
    @Column(name="due_at",nullable=false) private Instant dueAt;
    @Column(name="reminder_due_at",nullable=false) private Instant reminderDueAt;
    @Column(name="last_event_key",length=200) private String lastEventKey;
    public boolean processed(String key){
        if(key.equals(lastEventKey))return true;
        return lastEventKey!=null&&lastEventKey.startsWith("HISTORY:")&&key.startsWith("HISTORY:")
                &&Long.parseLong(key.substring(8))<=Long.parseLong(lastEventKey.substring(8));
    }
    public void processedEvent(String key){lastEventKey=key;}
    @Column(name="paused_at") private Instant pausedAt;
    @Column(name="reminded_at") private Instant remindedAt;
    @Column(name="breached_at") private Instant breachedAt;
    protected SlaState(){}
    public SlaState(Long request,SlaPolicy policy,Instant start){requestId=request;phase=policy.getPhase();restart(policy,start);}
    public void restart(SlaPolicy policy,Instant now){cycle++;durationSeconds=policy.getDurationSeconds();reminderSeconds=policy.getReminderSeconds();startedAt=now;dueAt=now.plusSeconds(durationSeconds);reminderDueAt=dueAt.minusSeconds(reminderSeconds);status="ACTIVE";pausedAt=null;remindedAt=null;breachedAt=null;}
    public void pause(Instant now){if(status.equals("ACTIVE")){status="PAUSED";pausedAt=now;}}
    public void resume(Instant now){if(status.equals("PAUSED")){dueAt=dueAt.plusSeconds(Math.max(0,Duration.between(pausedAt,now).getSeconds()));reminderDueAt=dueAt.minusSeconds(reminderSeconds);pausedAt=null;status="ACTIVE";}}
    public void complete(){status="COMPLETED";pausedAt=null;}
    public boolean breachDue(Instant now){return status.equals("ACTIVE")&&breachedAt==null&&now.isAfter(dueAt);}
    public boolean reminderDue(Instant now){return status.equals("ACTIVE")&&breachedAt==null&&remindedAt==null&&reminderSeconds>0&&!now.isAfter(dueAt)&&!now.isBefore(dueAt.minusSeconds(reminderSeconds));}
    public void breached(Instant now){breachedAt=now;}public void reminded(Instant now){remindedAt=now;}
    public Long getId(){return id;}public Long getRequestId(){return requestId;}public SlaPhase getPhase(){return phase;}
    public int getCycle(){return cycle;}public String getStatus(){return status;}public Instant getDueAt(){return dueAt;}
    public Instant getBreachedAt(){return breachedAt;}public Instant getRemindedAt(){return remindedAt;}
    public Snapshot snapshot(){return new Snapshot(id,requestId,phase,cycle,status,startedAt,dueAt,pausedAt,remindedAt,breachedAt);}
    public record Snapshot(Long id,Long requestId,SlaPhase phase,int cycle,String status,Instant startedAt,Instant dueAt,Instant pausedAt,Instant remindedAt,Instant breachedAt){}
}
