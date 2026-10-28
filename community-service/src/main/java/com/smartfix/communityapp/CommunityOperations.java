package com.smartfix.communityapp;

import com.fasterxml.jackson.databind.*;
import com.smartfix.community.service.*;
import com.smartfix.community.dto.*;
import com.smartfix.community.domain.CommunityCategory;
import jakarta.validation.Validator;
import java.util.*;
import java.security.MessageDigest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.smartfix.common.exception.*;

/** Explicit operation allowlist. Actor comes exclusively from a verified JWT. */
@Service
public class CommunityOperations {
    private final CommunityQueryService queries; private final CommunityQuestionService questions;
    private final CommunityAnswerService answers; private final CommunityModerationService moderation;
    private final JdbcTemplate db; private final ObjectMapper mapper; private final Validator validator;
    static final Set<String> READS=Set.of("browse","myQuestions","detail","ownAnswer","thread","myAnswers","reports","openCount");
    public CommunityOperations(CommunityQueryService queries,CommunityQuestionService questions,CommunityAnswerService answers,CommunityModerationService moderation,JdbcTemplate db,ObjectMapper mapper,Validator validator){this.queries=queries;this.questions=questions;this.answers=answers;this.moderation=moderation;this.db=db;this.mapper=mapper;this.validator=validator;}
    @Transactional(isolation=Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public Object execute(long actor,String operation,JsonNode p,UUID key)throws Exception {
        if(READS.contains(operation)) return read(actor,operation,p);
        if(!Set.of("ask","editQuestion","withdrawQuestion","answer","editAnswer","withdrawAnswer","accept","removeAcceptance","reportQuestion","reportAnswer","resolve","hideQuestion","restoreQuestion","hideAnswer","restoreAnswer").contains(operation))throw new InputValidationException("Unknown operation");
        if(key==null)throw new InputValidationException("Command key required");
        // Locks first-created command records across processes; also orders each actor's posting.
        db.update("INSERT INTO community_posting_guards(user_id) VALUES (?) ON CONFLICT DO NOTHING",actor);
        db.queryForObject("SELECT user_id FROM community_posting_guards WHERE user_id=? FOR UPDATE",Long.class,actor);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(mapper.convertValue(p,Map.class))));
        var prior=db.queryForList("SELECT request_hash,result FROM community_commands WHERE actor_id=? AND command_key=? AND operation=?",actor,key,operation);
        if(!prior.isEmpty()){
            if(!hash.equals(prior.getFirst().get("request_hash")))throw new BusinessConflictException("Command key reused with different input");
            return mapper.readTree(prior.getFirst().get("result").toString());
        }
        long id=p.path("id").asLong(); Object result;
        switch(operation){
            case "ask" -> result=questions.ask(command(p,QuestionFormCommand.class),actor);
            case "editQuestion" -> {questions.edit(id,command(p,QuestionFormCommand.class),actor);result=true;}
            case "withdrawQuestion" -> {questions.withdraw(id,actor);result=true;}
            case "answer" -> result=answers.post(id,command(p,AnswerFormCommand.class),actor);
            case "editAnswer" -> {answers.edit(id,command(p,AnswerFormCommand.class),actor);result=true;}
            case "withdrawAnswer" -> {answers.withdraw(id,actor);result=true;}
            case "accept" -> {answers.accept(id,p.path("answerId").asLong(),actor);result=true;}
            case "removeAcceptance" -> {answers.removeAcceptance(id,actor);result=true;}
            case "reportQuestion" -> result=moderation.reportQuestion(id,command(p,ReportContentCommand.class),actor);
            case "reportAnswer" -> result=moderation.reportAnswer(id,command(p,ReportContentCommand.class),actor);
            case "resolve" -> result=moderation.resolveReport(id,command(p,ResolveReportCommand.class),actor);
            case "hideQuestion" -> result=moderation.hideQuestion(id,actor);
            case "restoreQuestion" -> result=moderation.restoreQuestion(id,actor);
            case "hideAnswer" -> result=moderation.hideAnswer(id,actor);
            case "restoreAnswer" -> result=moderation.restoreAnswer(id,actor);
            default -> throw new InputValidationException("Unknown operation");
        }
        db.update("INSERT INTO community_commands(actor_id,command_key,operation,request_hash,result) VALUES (?,?,?,?,?)",actor,key,operation,hash,mapper.writeValueAsString(result));
        return result;
    }
    private <T>T command(JsonNode p,Class<T> type)throws Exception{T command=mapper.treeToValue(p.path("command"),type);if(command==null||!validator.validate(command).isEmpty())throw new InputValidationException("Invalid form");return command;}
    private Object read(long actor,String op,JsonNode p){
        long id=p.path("id").asLong();int page=p.path("page").asInt(),size=p.path("size").asInt();
        Object result=switch(op){
            case "browse" -> queries.browse(actor,p.hasNonNull("category")?CommunityCategory.valueOf(p.path("category").asText()):null,p.hasNonNull("filter")?QuestionFilter.valueOf(p.path("filter").asText()):null,p.path("query").asText(null),page,size);
            case "myQuestions" -> queries.listMyQuestions(actor,page,size);
            case "myAnswers" -> queries.listMyAnswers(actor,page,size);
            case "detail" -> queries.findQuestionDetail(actor,id);
            case "ownAnswer" -> queries.findOwnAnswer(actor,id);
            case "thread" -> queries.findThreadIdOfAnswer(actor,id);
            case "reports" -> moderation.listReports(actor,p.hasNonNull("view")?ReportView.valueOf(p.path("view").asText()):ReportView.OPEN,page,size);
            case "openCount" -> moderation.countOpenReports(actor);
            default -> throw new InputValidationException("Unknown operation");
        };
        if(result instanceof Page<?> found)return Map.of("content",found.getContent(),"page",found.getNumber(),"size",found.getSize(),"total",found.getTotalElements());
        return result;
    }
}
