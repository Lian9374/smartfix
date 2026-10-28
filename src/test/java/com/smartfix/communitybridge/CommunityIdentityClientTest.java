package com.smartfix.communitybridge;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import java.nio.file.*;import java.security.*;import java.util.*;
import com.smartfix.user.service.UserService;import com.smartfix.user.dto.*;import com.smartfix.user.domain.*;import com.smartfix.auth.security.SmartFixUserDetails;import org.springframework.security.access.AccessDeniedException;
class CommunityIdentityClientTest {
 @TempDir Path dir;
 @Test void checksCurrentAccountAndBatchesDisplayNames()throws Exception{
  var users=mock(UserService.class);var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);var keys=g.generateKeyPair();var priv=dir.resolve("private.pk8");var pub=dir.resolve("public.der");Files.write(priv,keys.getPrivate().getEncoded());Files.write(pub,keys.getPublic().getEncoded());var client=new CommunityIdentityClient(users,priv.toString(),pub.toString());var principal=new SmartFixUserDetails(new UserAuthenticationData(10L,"synthetic","not-a-real-hash",Role.REQUESTER,AccountStatus.ACTIVE,0));
  when(users.getUserAccess(10L)).thenReturn(new UserAccessResponse(10L,Role.REQUESTER,AccountStatus.ACTIVE,0,false));assertEquals(3,client.issue(principal).split("[.]").length);
  for(var access:List.of(new UserAccessResponse(10L,Role.REQUESTER,AccountStatus.DISABLED,0,false),new UserAccessResponse(10L,Role.ADMINISTRATOR,AccountStatus.ACTIVE,0,false),new UserAccessResponse(10L,Role.REQUESTER,AccountStatus.ACTIVE,1,false),new UserAccessResponse(10L,Role.REQUESTER,AccountStatus.ACTIVE,0,true))){when(users.getUserAccess(10L)).thenReturn(access);assertThrows(AccessDeniedException.class,()->client.issue(principal));}
  when(users.findDisplayNames(Set.of(10L,11L))).thenReturn(Map.of(10L,"A",11L,"B"));assertEquals(2,client.displayNames(List.of(10L,11L,10L)).size());verify(users,times(1)).findDisplayNames(Set.of(10L,11L));
 }
}
