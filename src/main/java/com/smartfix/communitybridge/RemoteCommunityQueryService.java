package com.smartfix.communitybridge;
import com.smartfix.community.service.*;
import com.smartfix.community.dto.*;
import com.smartfix.community.domain.*;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.*;
/** Compatibility adapter for the existing MVC controllers; no local business execution. */
@Service @Primary @Profile("community-remote")
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class RemoteCommunityQueryService extends CommunityQueryService {
 private final CommunityGateway gateway;
 public RemoteCommunityQueryService(CommunityGateway gateway){super(null,null,null,null,null);this.gateway=gateway;}
 private static Map<String,Object> nullable(Object... pairs){Map<String,Object> map=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)if(pairs[i+1]!=null)map.put(pairs[i].toString(),pairs[i+1]);return map;}
 @Override public Page<CommunityQuestionSummaryResponse> browse(Long actor, CommunityCategory category, QuestionFilter filter, String query, int page, int size){return gateway.page(actor,"browse",nullable("category",category,"filter",filter,"query",query,"page",page,"size",size),CommunityQuestionSummaryResponse.class);}
 @Override public Page<CommunityQuestionSummaryResponse> listMyQuestions(Long actor, int page, int size){return gateway.page(actor,"myQuestions",Map.of("page",page,"size",size),CommunityQuestionSummaryResponse.class);}
 @Override public Page<MyAnswerResponse> listMyAnswers(Long actor, int page, int size){return gateway.page(actor,"myAnswers",Map.of("page",page,"size",size),MyAnswerResponse.class);}
 @Override public CommunityQuestionDetailResponse findQuestionDetail(Long actor, Long id){return gateway.one(actor,"detail",Map.of("id",id),CommunityQuestionDetailResponse.class);}
 @Override public OwnAnswerResponse findOwnAnswer(Long actor, Long id){return gateway.one(actor,"ownAnswer",Map.of("id",id),OwnAnswerResponse.class);}
 @Override public Long findThreadIdOfAnswer(Long actor, Long id){return gateway.one(actor,"thread",Map.of("id",id),Long.class);}
}
