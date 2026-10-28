package com.smartfix.community.service;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.core.JdbcTemplate;
@Component
public class CommunityAccessGuard {
 private final JdbcTemplate db;
 public CommunityAccessGuard(JdbcTemplate db){this.db=db;}
 public void requireActiveUser(Long actor){
  var authentication=SecurityContextHolder.getContext().getAuthentication();
  if(authentication==null || !(authentication.getPrincipal() instanceof Jwt jwt) || actor==null || !actor.toString().equals(jwt.getSubject()))throw new AccessDeniedException("Trusted actor required");
 }
 public void requireAdministrator(Long actor){requireActiveUser(actor);Jwt jwt=(Jwt)SecurityContextHolder.getContext().getAuthentication().getPrincipal();if(!"ADMINISTRATOR".equals(jwt.getClaimAsString("role")))throw new AccessDeniedException("Administrator required");}
 public void serializePosting(Long actor){requireActiveUser(actor);db.update("INSERT INTO community_posting_guards(user_id) VALUES (?) ON CONFLICT DO NOTHING",actor);db.queryForObject("SELECT user_id FROM community_posting_guards WHERE user_id=? FOR UPDATE",Long.class,actor);}
}
