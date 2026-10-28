package com.smartfix.communityapp;
import org.springframework.web.bind.annotation.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.security.oauth2.jwt.Jwt;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.web.server.ResponseStatusException;import org.springframework.http.HttpStatus;import java.util.*;
@RestController @RequestMapping("/internal/community/v1")
class CommunityIdentityController {
 private final JdbcTemplate db;CommunityIdentityController(JdbcTemplate db){this.db=db;}
 @GetMapping("/identity") Map<String,Object> identity(@AuthenticationPrincipal Jwt jwt){return Map.of("userId",Long.parseLong(jwt.getSubject()),"role",jwt.getClaimAsString("role"));}
 @GetMapping("/moderation/access-check") Map<String,Boolean> admin(){return Map.of("allowed",true);}
 @GetMapping("/questions/{id}/edit-context") Map<String,Object> own(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){
  var rows=db.queryForList("SELECT id,author_id,title,body,status FROM community_questions WHERE id=? AND author_id=?",id,Long.parseLong(jwt.getSubject()));if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);var q=rows.get(0);q.put("editable","VISIBLE".equals(q.get("status")));return q;
 }
}
