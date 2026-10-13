package com.smartfix.request.service;
import com.smartfix.request.config.AttachmentProperties;
import com.smartfix.request.repository.AttachmentRepository;
import com.smartfix.user.service.NotificationContactService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.time.*;
import java.util.*;
/** Read-only reconciliation: never deletes a file while an upload/metadata transaction may be running. */
@Service
public class AttachmentInspectionService {
    private final AttachmentProperties properties;private final AttachmentRepository metadata;private final NotificationContactService access;private final Clock clock;
    public AttachmentInspectionService(AttachmentProperties properties,AttachmentRepository metadata,NotificationContactService access,Clock clock){this.properties=properties;this.metadata=metadata;this.access=access;this.clock=clock;}
    @Transactional(readOnly=true)
    public Inspection inspect(Long actor){
        access.requireAdmin(actor);Path directory=properties.getDir().toAbsolutePath().normalize();
        if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS))return new Inspection(List.of(),false);
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Attachment inspection requires a regular upload directory");
        Set<String> known=new HashSet<>(metadata.storedKeys());List<String> candidates=new ArrayList<>();boolean truncated=false;
        try(var files=Files.list(directory)){
            var iterator=files.iterator();int checked=0;
            while(iterator.hasNext()){
                if(checked++>=10000){truncated=true;break;}
                Path file=iterator.next();String key=file.getFileName().toString();
                if(!key.matches("[0-9a-fA-F-]{36}\\.(png|jpg)(\\.part)?")||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                if(!known.contains(key)&&Files.getLastModifiedTime(file,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(clock.instant().minus(Duration.ofDays(1))))candidates.add(key);
            }
        }catch(java.io.IOException failure){throw new IllegalStateException("Attachment inspection unavailable",failure);}
        candidates.sort(String::compareTo);return new Inspection(List.copyOf(candidates),truncated);
    }
    public record Inspection(List<String> candidates,boolean truncated){}
}
