package com.smartfix.communityapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfix.community.event.*;
import com.smartfix.community.domain.CommunityContentStatus;
import java.time.Instant;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class DomainOutbox {
    private final JdbcTemplate db; private final ObjectMapper mapper;
    public DomainOutbox(JdbcTemplate db,ObjectMapper mapper){this.db=db;this.mapper=mapper;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void append(long question,String type,Map<String,?> payload){
        long sequence=db.queryForObject("INSERT INTO community_event_sequences(question_id,sequence) VALUES (?,1) ON CONFLICT(question_id) DO UPDATE SET sequence=community_event_sequences.sequence+1 RETURNING sequence",Long.class,question);
        UUID id=UUID.randomUUID();
        try{
            String body=mapper.writeValueAsString(Map.of("eventId",id,"traceId",id,"producer","community","eventType",type,"schemaVersion",1,"aggregateType","QUESTION","aggregateId",question,"aggregateSequence",sequence,"occurredAt",Instant.now().toString(),"payload",payload));
            db.update("INSERT INTO community_outbox(event_id,question_id,aggregate_sequence,payload,status) VALUES (?,?,?,?,'PENDING')",id,question,sequence,body);
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Event serialization failed",e);}
    }
    @EventListener public void answered(CommunityAnswerCreatedEvent e){append(e.questionId(),"CommunityAnswerCreated",Map.of("answerId",e.answerId(),"questionId",e.questionId(),"questionAuthorId",e.questionAuthorId(),"answerAuthorId",e.answerAuthorId()));}
    @EventListener public void accepted(CommunityAnswerAcceptedEvent e){append(e.questionId(),"CommunityAnswerAccepted",Map.of("answerId",e.answerId(),"questionId",e.questionId(),"questionAuthorId",e.questionAuthorId(),"answerAuthorId",e.answerAuthorId()));}
    @EventListener public void hidden(CommunityContentHiddenEvent e){if(e.resultingStatus()==CommunityContentStatus.HIDDEN)append(e.questionId(),"CommunityContentHidden",Map.of("questionId",e.questionId(),"contentId",e.contentId(),"contentType",e.contentType().name(),"authorId",e.authorId()));}
}
