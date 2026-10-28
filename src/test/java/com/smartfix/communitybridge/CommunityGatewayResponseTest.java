package com.smartfix.communitybridge;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;

class CommunityGatewayResponseTest {
    @Test void concurrentCommunityCallIsRejectedImmediatelyWhenBudgetIsUsed() throws Exception {
        var identity=org.mockito.Mockito.mock(CommunityIdentityClient.class);
        var actor=org.mockito.Mockito.mock(com.smartfix.auth.security.SmartFixUserDetails.class);
        org.mockito.Mockito.when(actor.getUserId()).thenReturn(1L);
        org.mockito.Mockito.when(identity.issue(actor)).thenReturn("synthetic-test-token");
        var builder=RestClient.builder().baseUrl("http://community");var server=MockRestServiceServer.bindTo(builder).build();
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        server.expect(requestTo("http://community/operations/thread")).andRespond(request->{
            entered.countDown();try{assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){throw new java.io.IOException(e);}
            return withSuccess("1",MediaType.APPLICATION_JSON).createResponse(request);
        });
        var gateway=new CommunityGateway(identity,new com.fasterxml.jackson.databind.ObjectMapper(),builder.build(),1);
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor,null,java.util.List.of());
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()){
            var first=executor.submit(()->{org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);try{return gateway.call(1L,"thread",java.util.Map.of("id",1));}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}});
            try{
                assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->gateway.call(1L,"thread",java.util.Map.of("id",1)));
                assertEquals(503,error.getStatusCode().value());
            }finally{release.countDown();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
            assertEquals(1,first.get().asInt());server.verify();
        }
    }
    @Test void scalarCommandResultRetainsLongPrecision() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://community/answer")).andRespond(withSuccess("900000000002",MediaType.APPLICATION_JSON));
        JsonNode value=builder.build().post().uri("http://community/answer").body(java.util.Map.of("body","test")).retrieve().body(JsonNode.class);
        assertEquals(900000000002L,value.longValue());server.verify();
    }
}
