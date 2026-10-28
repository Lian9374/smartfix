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
public class RemoteCommunityAnswerService extends CommunityAnswerService {
 private final CommunityGateway gateway;
 public RemoteCommunityAnswerService(CommunityGateway gateway){super(null,null,null,null,null,null);this.gateway=gateway;}
 private static Map<String,Object> nullable(Object... pairs){Map<String,Object> map=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)if(pairs[i+1]!=null)map.put(pairs[i].toString(),pairs[i+1]);return map;}
 @Override public Long post(Long id, AnswerFormCommand command, Long actor){return gateway.one(actor,"answer",Map.of("id",id,"command",command),Long.class);}
 @Override public void edit(Long id, AnswerFormCommand command, Long actor){gateway.call(actor,"editAnswer",Map.of("id",id,"command",command));}
 @Override public void withdraw(Long id, Long actor){gateway.call(actor,"withdrawAnswer",Map.of("id",id));}
 @Override public void accept(Long id, Long answerId, Long actor){gateway.call(actor,"accept",Map.of("id",id,"answerId",answerId));}
 @Override public void removeAcceptance(Long id, Long actor){gateway.call(actor,"removeAcceptance",Map.of("id",id));}
}
