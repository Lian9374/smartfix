package com.smartfix.communityapp;
import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.config.http.SessionCreationPolicy;import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.*;import org.springframework.security.oauth2.core.*;import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;import org.springframework.security.oauth2.server.resource.authentication.*;import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.nio.file.*;import java.security.*;import java.security.interfaces.*;import java.security.spec.*;import java.time.*;import java.util.*;
@Configuration @ConditionalOnProperty(name="smartfix.community.identity.enabled",havingValue="true")
class CommunityJwtSecurity {
 static OAuth2TokenValidator<Jwt> validator(){return jwt->{try{
  Instant now=Instant.now();String role=jwt.getClaimAsString("role");Object version=jwt.getClaims().get("securityVersion");
  boolean ok="RS256".equals(jwt.getHeaders().get("alg"))&&"stage4-core-1".equals(jwt.getHeaders().get("kid"))&&"smartfix-core".equals(jwt.getClaimAsString("iss"))&&jwt.getAudience().contains("smartfix-community")&&Long.parseLong(jwt.getSubject())>0&&Set.of("REQUESTER","TECHNICIAN","ADMINISTRATOR").contains(role)&&Boolean.FALSE.equals(jwt.getClaims().get("passwordChangeRequired"))&&version instanceof Number&&((Number)version).longValue()>=0&&jwt.getIssuedAt()!=null&&jwt.getNotBefore()!=null&&jwt.getExpiresAt()!=null&&!jwt.getNotBefore().isBefore(jwt.getIssuedAt())&&!jwt.getNotBefore().isAfter(jwt.getExpiresAt())&&jwt.getExpiresAt().isAfter(jwt.getIssuedAt())&&Duration.between(jwt.getIssuedAt(),jwt.getExpiresAt()).getSeconds()<=30&&!jwt.getIssuedAt().isAfter(now.plusSeconds(5))&&!jwt.getNotBefore().isAfter(now.plusSeconds(5))&&jwt.getExpiresAt().isAfter(now.minusSeconds(5));
  if(ok)return OAuth2TokenValidatorResult.success();
 }catch(Exception ignored){}return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Invalid service identity",null));};}
 @Bean JwtDecoder communityDecoder(@Value("${smartfix.community.identity.public-key}")String path)throws Exception{
  var key=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Path.of(path))));var d=NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();d.setJwtValidator(validator());return d;
 }
 @Bean SecurityFilterChain communityApi(HttpSecurity http)throws Exception{
  JwtAuthenticationConverter c=new JwtAuthenticationConverter();c.setJwtGrantedAuthoritiesConverter(j->List.of(new SimpleGrantedAuthority("ROLE_"+j.getClaimAsString("role"))));
  return http.csrf(csrf->csrf.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll().requestMatchers("/actuator/health").permitAll().requestMatchers("/internal/community/v1/moderation/**","/internal/community/v1/audit").hasRole("ADMINISTRATOR").requestMatchers("/internal/community/v1/**").authenticated().anyRequest().denyAll()).oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(c))).build();
 }
}
