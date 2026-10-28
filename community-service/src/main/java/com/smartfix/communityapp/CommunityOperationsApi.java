package com.smartfix.communityapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfix.common.exception.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.*;

@RestController
@RequestMapping("/internal/community/v1")
public class CommunityOperationsApi {
    private final CommunityOperations operations;
    public CommunityOperationsApi(CommunityOperations operations){this.operations=operations;}
    @PostMapping("/operations/{operation}")
    Object execute(@PathVariable String operation,@RequestBody JsonNode parameters,@RequestHeader(value="Idempotency-Key",required=false) UUID key,@AuthenticationPrincipal Jwt principal)throws Exception{
        return operations.execute(Long.parseLong(principal.getSubject()),operation,parameters,key);
    }
    @ExceptionHandler(ResourceNotFoundException.class) ResponseEntity<?> absent(ResourceNotFoundException e){return businessError(404,e);}
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?> forbidden(){return error(403);}
    @ExceptionHandler(InputValidationException.class) ResponseEntity<?> invalidForm(InputValidationException e){return businessError(400,e);}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,com.fasterxml.jackson.core.JsonProcessingException.class}) ResponseEntity<?> invalid(){return error(400);}
    @ExceptionHandler(BusinessConflictException.class) ResponseEntity<?> businessConflict(BusinessConflictException e){return businessError(409,e);}
    @ExceptionHandler({DataIntegrityViolationException.class,OptimisticLockingFailureException.class}) ResponseEntity<?> conflict(){return error(409);}
    private ResponseEntity<?> businessError(int status,RuntimeException e){return ResponseEntity.status(status).body(Map.of("code","COMMUNITY_"+status,"message",e.getMessage()));}
    private ResponseEntity<?> error(int status){return ResponseEntity.status(status).body(Map.of("code","COMMUNITY_"+status));}
}
