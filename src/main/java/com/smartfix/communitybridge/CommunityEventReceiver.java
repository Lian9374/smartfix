package com.smartfix.communitybridge;
import org.springframework.context.annotation.*;import org.springframework.core.annotation.Order;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.beans.factory.annotation.Value;import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.config.http.SessionCreationPolicy;import org.springframework.security.web.SecurityFilterChain;import org.springframework.security.oauth2.jwt.*;import org.springframework.security.oauth2.core.*;import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;import java.nio.file.*;import java.security.*;import java.security.interfaces.*;import java.security.spec.*;import java.time.*;import java.util.*;import com.fasterxml.jackson.databind.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.Transactional;import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;import org.springframework.http.HttpStatus;import com.smartfix.notification.repository.NotificationInsertRepository;
@RestController @ConditionalOnProperty(name="smartfix.community.events.enabled",havingValue="true")
public class CommunityEventReceiver {
 private final JdbcTemplate db;private final NotificationInsertRepository notifications;private final ObjectMapper mapper;
 public CommunityEventReceiver(JdbcTemplate db,NotificationInsertRepository notifications,ObjectMapper mapper){this.db=db;this.notifications=notifications;this.mapper=mapper;}
 @Bean @Order(0) SecurityFilterChain eventSecurity(HttpSecurity http,@Value("${smartfix.community.events.public-key}")String path)throws Exception{
  var key=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Path.of(path))));var decoder=NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
  decoder.setJwtValidator(j->{try{Instant now=Instant.now();if("smartfix-community".equals(j.getClaimAsString("iss"))&&j.getAudience().contains("smartfix-core-events")&&"community-publisher".equals(j.getSubject())&&"stage5-community-1".equals(j.getHeaders().get("kid"))&&j.getIssuedAt()!=null&&j.getNotBefore()!=null&&j.getExpiresAt()!=null&&!j.getIssuedAt().isAfter(now.plusSeconds(5))&&!j.getNotBefore().isAfter(now.plusSeconds(5))&&j.getExpiresAt().isAfter(now.minusSeconds(5))&&j.getExpiresAt().isAfter(j.getIssuedAt())&&Duration.between(j.getIssuedAt(),j.getExpiresAt()).compareTo(Duration.ofSeconds(30))<=0)return OAuth2TokenValidatorResult.success();}catch(Exception ignored){}return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));});
  return http.securityMatcher("/internal/community-events/v1/events").csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.anyRequest().authenticated()).oauth2ResourceServer(o->o.jwt(j->j.decoder(decoder))).build();
 }
 @PostMapping("/internal/community-events/v1/events") @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> receive(@RequestBody JsonNode event)throws Exception{
  String type=event.path("eventType").asText();
  if(event.path("schemaVersion").asInt()!=1||!"community".equals(event.path("producer").asText())||!"QUESTION".equals(event.path("aggregateType").asText())||!Set.of("CommunityAnswerCreated","CommunityAnswerAccepted","CommunityContentHidden","CommunityAuditRecorded").contains(type))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
  UUID id;try{id=UUID.fromString(event.path("eventId").asText());}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST);}
  long q=event.path("aggregateId").asLong(),seq=event.path("aggregateSequence").asLong();JsonNode p=event.path("payload");
  if(q<=0||seq<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(mapper.convertValue(event,Map.class))));
  db.queryForList("SELECT pg_advisory_xact_lock(?)",q);
  var old=db.queryForList("SELECT request_hash FROM core_community_inbox WHERE event_id=?",id);
  if(!old.isEmpty()){if(!hash.equals(old.getFirst().get("request_hash")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Event content changed");return Map.of("accepted",true,"eventId",id.toString(),"duplicate",true);}
  long previous=db.queryForObject("SELECT COALESCE(max(aggregate_sequence),0) FROM core_community_inbox WHERE question_id=?",Long.class,q);if(seq!=previous+1)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Earlier event pending");
  db.update("INSERT INTO core_community_inbox(event_id,request_hash,question_id,aggregate_sequence) VALUES (?,?,?,?)",id,hash,q,seq);
  if(type.equals("CommunityAuditRecorded")){
   if(p.path("auditId").asLong()<=0||p.path("actorUserId").asLong()<=0||!Set.of("QUESTION","ANSWER","REPORT").contains(p.path("targetType").asText()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
   db.update("INSERT INTO audit_entries(actor_user_id,action,target_type,target_id,outcome,occurred_at,source,source_id) VALUES (?,?,?,?,?,?,'COMMUNITY',?) ON CONFLICT(source,source_id) WHERE source_id IS NOT NULL DO NOTHING",p.path("actorUserId").asLong(),p.path("action").asText(),p.path("targetType").asText(),p.path("targetId").asLong(),p.path("outcome").asText(),java.sql.Timestamp.from(Instant.parse(p.path("occurredAt").asText())),p.path("auditId").asLong());
  }else{
   long recipient,actor;String title,message,eventType;Long reference=q;
   if(type.equals("CommunityContentHidden")){
    recipient=p.path("authorId").asLong();actor=0;title="Community content hidden";message="A moderator hid your "+p.path("contentType").asText().toLowerCase(Locale.ROOT)+". It is no longer publicly visible.";eventType="COMMUNITY_CONTENT_HIDDEN";reference=null;
   }else{
    boolean accepted=type.equals("CommunityAnswerAccepted");recipient=p.path(accepted?"answerAuthorId":"questionAuthorId").asLong();actor=p.path(accepted?"questionAuthorId":"answerAuthorId").asLong();
    if(actor<=0||p.path("questionId").asLong()!=q||p.path("answerId").asLong()<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    title=accepted?"Your answer was accepted":"New answer to your question";message=accepted?"The person who asked the question accepted your answer.":"Someone answered your campus question. Open the discussion to read their reply.";eventType=accepted?"COMMUNITY_ANSWER_ACCEPTED":"COMMUNITY_ANSWER_CREATED";
   }
   if(recipient<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
   if(recipient!=actor){String dedup="COMMUNITY_EVENT:"+id;
    db.update("INSERT INTO delivery_tasks(recipient_id,channel,event_type,dedup_key,title,message,reference_id,status,attempts,next_attempt_at,created_at,delivered_at) VALUES (?,'IN_APP',?,?,?,?,?,'DELIVERED',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",recipient,eventType,dedup,title,message,reference);
    notifications.insertIfAbsent(recipient,eventType,title,message,reference,dedup,Instant.now());
   }
  }
  return Map.of("accepted",true,"eventId",id.toString(),"duplicate",false);
 }
 @ExceptionHandler(ResponseStatusException.class) org.springframework.http.ResponseEntity<Map<String,String>> statusError(ResponseStatusException e){return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(Map.of("code","REQUEST_REFUSED"));}
}
