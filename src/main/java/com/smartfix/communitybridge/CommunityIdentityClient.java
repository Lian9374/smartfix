package com.smartfix.communitybridge;
import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.user.service.UserService;
import com.smartfix.user.domain.AccountStatus;
import java.nio.file.*;import java.security.*;import java.security.interfaces.*;import java.security.spec.*;import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;import org.springframework.security.oauth2.jwt.*;import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import com.nimbusds.jose.jwk.*;import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.web.client.RestClient;import org.springframework.http.client.SimpleClientHttpRequestFactory;
@Service @ConditionalOnProperty(name="smartfix.community.identity.enabled",havingValue="true")
public class CommunityIdentityClient {
 private final UserService users;private final JwtEncoder encoder;private final RestClient client;
 public CommunityIdentityClient(UserService users,@Value("${smartfix.community.identity.private-key}")String privatePath,@Value("${smartfix.community.identity.public-key}")String publicPath)throws Exception{
  this.users=users;KeyFactory f=KeyFactory.getInstance("RSA");var priv=(RSAPrivateKey)f.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(Path.of(privatePath))));var pub=(RSAPublicKey)f.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Path.of(publicPath))));
  encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(new com.nimbusds.jose.jwk.RSAKey.Builder(pub).privateKey(priv).keyID("stage4-core-1").build())));
  var http=new SimpleClientHttpRequestFactory();http.setConnectTimeout(1000);http.setReadTimeout(3000);client=RestClient.builder().baseUrl("http://127.0.0.1:19092").requestFactory(http).build();
 }
 public String issue(SmartFixUserDetails principal){
  var a=users.getUserAccess(principal.getUserId());
  if(a.accountStatus()!=AccountStatus.ACTIVE||a.passwordChangeRequired()||a.securityVersion()!=principal.getSecurityVersion()||a.role()!=principal.getRole())throw new AccessDeniedException("Account access changed");
  Instant now=Instant.now();var claims=JwtClaimsSet.builder().issuer("smartfix-core").audience(List.of("smartfix-community")).subject(a.userId().toString()).issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(30)).claim("role",a.role().name()).claim("securityVersion",a.securityVersion()).claim("passwordChangeRequired",false).build();
  return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("stage4-core-1").build(),claims)).getTokenValue();
 }
 public Map<?,?> inspect(SmartFixUserDetails principal){return client.get().uri("/internal/community/v1/identity").headers(h->h.setBearerAuth(issue(principal))).retrieve().body(Map.class);}
 public Map<Long,String> displayNames(Collection<Long> ids){if(ids.size()>50)throw new IllegalArgumentException("Page too large");return users.findDisplayNames(new HashSet<>(ids));}
}
