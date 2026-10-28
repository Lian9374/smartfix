package com.smartfix.communityapp;
import org.springframework.stereotype.Service;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.*;import org.springframework.http.HttpStatus;import org.springframework.web.server.ResponseStatusException;import com.fasterxml.jackson.databind.ObjectMapper;import java.util.*;import java.time.Instant;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;
@org.springframework.context.annotation.Profile("!community-full")
@Service
public class RemoteAnswerService {
 public static final long DEMO=900000000001L;
 private final JdbcTemplate db;private final CommunityPostingGuard posting;private final ObjectMapper mapper;
 public RemoteAnswerService(JdbcTemplate db,CommunityPostingGuard posting,ObjectMapper mapper){this.db=db;this.posting=posting;this.mapper=mapper;}
 @Transactional(isolation=Isolation.READ_COMMITTED)
 public Map<String,Object> submit(long actor,long question,String body,UUID key){
  if(question!=DEMO)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  if(body==null||body.trim().length()<10||body.trim().length()>4000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  String text=body.trim(),hash=hash(question+":"+text);
  db.update("INSERT INTO community_posting_guards(user_id) VALUES (?) ON CONFLICT DO NOTHING",actor);db.queryForObject("SELECT user_id FROM community_posting_guards WHERE user_id=? FOR UPDATE",Long.class,actor);
  var prior=db.queryForList("SELECT request_hash,answer_id,event_id FROM community_answer_commands WHERE actor_id=? AND command_key=?",actor,key);
  if(!prior.isEmpty()){var p=prior.get(0);if(!hash.equals(p.get("request_hash")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key reused");return Map.of("answerId",p.get("answer_id"),"eventId",p.get("event_id").toString(),"duplicate",true);}
  var q=db.queryForList("SELECT author_id,status FROM community_questions WHERE id=?",question);if(q.isEmpty()||!"VISIBLE".equals(q.get(0).get("status")))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  return posting.answer(actor,()->{
   long answer=db.queryForObject("INSERT INTO community_answers(question_id,author_id,body) VALUES (?,?,?) RETURNING id",Long.class,question,actor,text);
   long seq=db.queryForObject("UPDATE community_questions SET version=version+1 WHERE id=? RETURNING version",Long.class,question);UUID event=UUID.randomUUID();
   try{String payload=mapper.writeValueAsString(Map.of("traceId",event.toString(),"aggregateType","QUESTION","eventId",event.toString(),"eventType","CommunityAnswerCreated","schemaVersion",1,"producer","community","aggregateId",question,"aggregateSequence",seq,"occurredAt",Instant.now().toString(),"payload",Map.of("answerId",answer,"questionId",question,"questionAuthorId",q.get(0).get("author_id"),"answerAuthorId",actor)));
    db.update("INSERT INTO community_outbox(event_id,question_id,aggregate_sequence,payload,status) VALUES (?,?,?,?,'PENDING')",event,question,seq,payload);
    db.update("INSERT INTO community_answer_commands(actor_id,command_key,request_hash,answer_id,event_id) VALUES (?,?,?,?,?)",actor,key,hash,answer,event);
    return Map.<String,Object>of("answerId",answer,"eventId",event.toString(),"duplicate",false);
   }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Event serialization failed",e);}
  });
 }
 @Transactional(readOnly=true)
 public Map<String,Object> detail(long question,long actor,String role){
  if(question!=DEMO)throw new ResponseStatusException(HttpStatus.NOT_FOUND);var rows=db.queryForList("SELECT id,author_id,title,body,category,status FROM community_questions WHERE id=?",question);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);var q=rows.get(0);if(!"VISIBLE".equals(q.get("status"))&&((Number)q.get("author_id")).longValue()!=actor)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  q.put("answers",db.query("SELECT id,author_id,body,status FROM community_answers WHERE question_id=? ORDER BY created_at,id",(rs,n)->{var a=new HashMap<String,Object>();a.put("id",rs.getLong("id"));a.put("author_id",rs.getLong("author_id"));a.put("status",rs.getString("status"));a.put("body","VISIBLE".equals(rs.getString("status"))||rs.getLong("author_id")==actor||"ADMINISTRATOR".equals(role)?rs.getString("body"):null);return a;},question));return q;
 }
 static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
