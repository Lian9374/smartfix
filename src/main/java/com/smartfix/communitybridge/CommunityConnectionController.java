package com.smartfix.communitybridge;
import org.springframework.web.bind.annotation.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import com.smartfix.auth.security.SmartFixUserDetails;import java.util.*;
@RestController @ConditionalOnProperty(name="smartfix.community.identity.enabled",havingValue="true")
class CommunityConnectionController {
 private final CommunityIdentityClient client;CommunityConnectionController(CommunityIdentityClient client){this.client=client;}
 @GetMapping("/community/connection") Map<String,Object> inspect(@AuthenticationPrincipal SmartFixUserDetails user){return Map.of("communityIdentity",client.inspect(user),"displayNames",client.displayNames(List.of(user.getUserId())));}
}
