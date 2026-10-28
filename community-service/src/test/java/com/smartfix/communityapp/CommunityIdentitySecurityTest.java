package com.smartfix.communityapp;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.security.*;import java.security.interfaces.*;import java.time.*;import java.util.*;
import org.springframework.security.oauth2.jwt.*;import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;import com.nimbusds.jose.jwk.*;import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import static org.junit.jupiter.api.Assertions.*;
class CommunityIdentitySecurityTest {
 @TempDir Path dir;
 JwtEncoder encoder(KeyPair k){return encoder(k,"stage4-core-1");}
 JwtEncoder encoder(KeyPair k,String kid){return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey)k.getPublic()).privateKey((RSAPrivateKey)k.getPrivate()).keyID(kid).build())));}
 String token(JwtEncoder e,String issuer,String audience,String kid,String role,boolean change,long offset){Instant now=Instant.now().plusSeconds(offset);return e.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build(),JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject("100").issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(30)).claim("role",role).claim("securityVersion",0).claim("passwordChangeRequired",change).build())).getTokenValue();}
 @Test void validatesRealSignaturesAndMandatoryIdentityClaims()throws Exception{
  KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);KeyPair k=g.generateKeyPair();Path pub=dir.resolve("public.der");Files.write(pub,k.getPublic().getEncoded());JwtDecoder d=new CommunityJwtSecurity().communityDecoder(pub.toString());JwtEncoder e=encoder(k);
  assertEquals("100",d.decode(token(e,"smartfix-core","smartfix-community","stage4-core-1","REQUESTER",false,0)).getSubject());
  for(String bad:List.of(token(e,"bad","smartfix-community","stage4-core-1","REQUESTER",false,0),token(e,"smartfix-core","bad","stage4-core-1","REQUESTER",false,0),token(encoder(k,"unknown"),"smartfix-core","smartfix-community","unknown","REQUESTER",false,0),token(e,"smartfix-core","smartfix-community","stage4-core-1","ROOT",false,0),token(e,"smartfix-core","smartfix-community","stage4-core-1","REQUESTER",true,0),token(e,"smartfix-core","smartfix-community","stage4-core-1","REQUESTER",false,-120),token(e,"smartfix-core","smartfix-community","stage4-core-1","REQUESTER",false,120),token(encoder(g.generateKeyPair()),"smartfix-core","smartfix-community","stage4-core-1","REQUESTER",false,0)))assertThrows(JwtException.class,()->d.decode(bad));
 }
}
