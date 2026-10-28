package com.smartfix.communitybridge;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.ComponentScan.Filter;

/** Remote Core cannot create Community repositories or map Community entities. */
@Configuration @Profile("community-remote")
@EnableJpaRepositories(basePackages="com.smartfix",excludeFilters=@Filter(type=FilterType.REGEX,pattern="com\\.smartfix\\.community\\.repository\\..*"))
@EntityScan(basePackages={"com.smartfix.audit.domain","com.smartfix.announcement.domain","com.smartfix.technician.domain","com.smartfix.dispatch.domain","com.smartfix.facility.domain","com.smartfix.sla.domain","com.smartfix.workorder.domain","com.smartfix.notification.domain","com.smartfix.user.domain","com.smartfix.request.domain"})
public class RemoteCorePersistence {
    @Bean org.springframework.boot.ApplicationRunner communityBoundary(org.springframework.context.ApplicationContext context,jakarta.persistence.EntityManagerFactory entities){
        return args -> {
            if(context.getBeanNamesForType(com.smartfix.community.repository.CommunityQuestionRepository.class).length!=0
                ||context.getBeanNamesForType(com.smartfix.community.repository.CommunityAnswerRepository.class).length!=0
                ||context.getBeanNamesForType(com.smartfix.community.repository.CommunityReportRepository.class).length!=0
                ||context.getBeanNamesForType(com.smartfix.notification.listener.CommunityNotificationListener.class).length!=0)
                throw new IllegalStateException("Community persistence or legacy listener active in remote Core");
            for(var entity:entities.getMetamodel().getEntities())
                if(entity.getJavaType().getPackageName().startsWith("com.smartfix.community."))throw new IllegalStateException("Community entity mapped in remote Core");
            if(!(context.getBean(com.smartfix.community.service.CommunityQueryService.class) instanceof RemoteCommunityQueryService)
                ||!(context.getBean(com.smartfix.community.service.CommunityQuestionService.class) instanceof RemoteCommunityQuestionService)
                ||!(context.getBean(com.smartfix.community.service.CommunityAnswerService.class) instanceof RemoteCommunityAnswerService)
                ||!(context.getBean(com.smartfix.community.service.CommunityModerationService.class) instanceof RemoteCommunityModerationService))
                throw new IllegalStateException("Remote Core adapter missing");
        };
    }
}
