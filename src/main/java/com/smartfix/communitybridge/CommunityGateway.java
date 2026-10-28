package com.smartfix.communitybridge;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Bounded, fail-fast synchronous calls. No Community SQL or Repository fallback. */
@Service @Profile("community-remote")
public class CommunityGateway {
    private final CommunityIdentityClient identity; private final ObjectMapper mapper; private final RestClient http;
    private final Semaphore slots;
    @org.springframework.beans.factory.annotation.Autowired
    public CommunityGateway(CommunityIdentityClient identity,ObjectMapper mapper,@org.springframework.beans.factory.annotation.Value("${smartfix.community.base-url:http://127.0.0.1:19092}") String baseUrl){
        this(identity,mapper,defaultClient(baseUrl),8);
    }
    CommunityGateway(CommunityIdentityClient identity,ObjectMapper mapper,RestClient http,int concurrency){
        if(concurrency<1||concurrency>64)throw new IllegalArgumentException("Invalid community concurrency limit");
        this.identity=identity;this.mapper=mapper;this.http=http;this.slots=new Semaphore(concurrency);
    }
    private static RestClient defaultClient(String baseUrl){
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(500);factory.setReadTimeout(2000);
        return RestClient.builder().baseUrl(baseUrl+"/internal/community/v1").requestFactory(factory).build();
    }
    public JsonNode call(Long actor,String operation,Map<String,?> parameters){
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null||!(authentication.getPrincipal() instanceof SmartFixUserDetails principal)||!principal.getUserId().equals(actor))throw new AccessDeniedException("Session actor required");
        if(!slots.tryAcquire())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Community busy; keep this form and retry");
        try{
            String token=identity.issue(principal);
            boolean writing=!Set.of("browse","myQuestions","myAnswers","detail","ownAnswer","thread","reports","openCount").contains(operation);
            var request=http.post().uri("/operations/"+operation).headers(h->{h.setBearerAuth(token);if(writing){var attrs=(ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();h.set("Idempotency-Key",RemoteFormKeys.require(attrs.getRequest(),actor).toString());}}).body(parameters);
            JsonNode result=request.retrieve().body(JsonNode.class);
            if(result==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Missing community response");
            return names(result);
        }catch(RestClientResponseException e){
            String message=null;
            try{message=mapper.readTree(e.getResponseBodyAsString()).path("message").asText(null);}catch(Exception ignored){}
            switch(e.getStatusCode().value()){
                case 400 -> throw new InputValidationException(message==null?"Check the submitted form.":message);
                case 403 -> throw new AccessDeniedException("This operation is not permitted.");
                case 404 -> throw new ResourceNotFoundException(message==null?"Community content not found.":message);
                case 409 -> throw new BusinessConflictException(message==null?"The content changed or this command was already used.":message);
                default -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Community unavailable; result unconfirmed. Retry the same form.");
            }
        }catch(RestClientException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Result unconfirmed. Keep this form and retry with its original key.");}
        finally{slots.release();}
    }
    public <T>T one(Long actor,String operation,Map<String,?> p,Class<T> type){return mapper.convertValue(call(actor,operation,p),type);}
    public <T>Page<T> page(Long actor,String operation,Map<String,?> p,Class<T> type){JsonNode n=call(actor,operation,p);List<T> content=new ArrayList<>();n.path("content").forEach(v->content.add(mapper.convertValue(v,type)));return new PageImpl<>(content,PageRequest.of(n.path("page").asInt(),n.path("size").asInt()),n.path("total").asLong());}
    private JsonNode names(JsonNode root){Set<Long> ids=new LinkedHashSet<>();collect(root,ids);Map<Long,String> names=new HashMap<>();var list=new ArrayList<>(ids);for(int i=0;i<list.size();i+=50)names.putAll(identity.displayNames(list.subList(i,Math.min(i+50,list.size()))));fill(root,names);return root;}
    private void collect(JsonNode n,Set<Long> ids){if(n.isObject()&&n.has("authorDisplayName")&&n.path("authorId").isIntegralNumber())ids.add(n.path("authorId").asLong());if(n.isContainerNode())n.forEach(v->collect(v,ids));}
    private void fill(JsonNode n,Map<Long,String> names){if(n instanceof ObjectNode object&&n.has("authorDisplayName"))object.put("authorDisplayName",names.getOrDefault(n.path("authorId").asLong(),"Campus member"));if(n.isContainerNode())n.forEach(v->fill(v,names));}
}
