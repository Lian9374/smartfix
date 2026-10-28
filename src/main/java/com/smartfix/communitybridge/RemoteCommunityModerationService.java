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
public class RemoteCommunityModerationService extends CommunityModerationService {
 private final CommunityGateway gateway;
 public RemoteCommunityModerationService(CommunityGateway gateway){super(null,null,null,null,null,null,null,null);this.gateway=gateway;}
 private static Map<String,Object> nullable(Object... pairs){Map<String,Object> map=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)if(pairs[i+1]!=null)map.put(pairs[i].toString(),pairs[i+1]);return map;}
 @Override public Long reportQuestion(Long id, ReportContentCommand command, Long actor){return gateway.one(actor,"reportQuestion",Map.of("id",id,"command",command),Long.class);}
 @Override public Long reportAnswer(Long id, ReportContentCommand command, Long actor){return gateway.one(actor,"reportAnswer",Map.of("id",id,"command",command),Long.class);}
 @Override public Page<CommunityReportResponse> listOpenReports(Long actor, int page, int size){return gateway.page(actor,"reports",Map.of("page",page,"size",size,"view","OPEN"),CommunityReportResponse.class);}
 @Override public long countOpenReports(Long actor){return gateway.one(actor,"openCount",Map.of(),Long.class);}
 @Override public Page<CommunityReportResponse> listReports(Long actor, ReportView view, int page, int size){return gateway.page(actor,"reports",nullable("page",page,"size",size,"view",view),CommunityReportResponse.class);}
 @Override public boolean resolveReport(Long id, ResolveReportCommand command, Long actor){return gateway.one(actor,"resolve",Map.of("id",id,"command",command),Boolean.class);}
 @Override public boolean hideQuestion(Long id, Long actor){return gateway.one(actor,"hideQuestion",Map.of("id",id),Boolean.class);}
 @Override public boolean restoreQuestion(Long id, Long actor){return gateway.one(actor,"restoreQuestion",Map.of("id",id),Boolean.class);}
 @Override public boolean hideAnswer(Long id, Long actor){return gateway.one(actor,"hideAnswer",Map.of("id",id),Boolean.class);}
 @Override public boolean restoreAnswer(Long id, Long actor){return gateway.one(actor,"restoreAnswer",Map.of("id",id),Boolean.class);}
}
