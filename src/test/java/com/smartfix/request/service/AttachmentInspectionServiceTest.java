package com.smartfix.request.service;
import com.smartfix.request.config.AttachmentProperties;
import com.smartfix.request.repository.AttachmentRepository;
import com.smartfix.user.service.NotificationContactService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AttachmentInspectionServiceTest {
    @TempDir Path directory;
    @Test void onlyAgedUnreferencedGeneratedKeysAreReportedAndNothingIsDeleted()throws Exception{
        var p=new AttachmentProperties();p.setDir(directory);var repository=mock(AttachmentRepository.class);var access=mock(NotificationContactService.class);
        String known=UUID.randomUUID()+".png",orphan=UUID.randomUUID()+".jpg",recent=UUID.randomUUID()+".png";
        Instant now=Instant.parse("2026-10-13T00:00:00Z");
        for(String key:List.of(known,orphan,recent,"unrelated.txt")){Files.writeString(directory.resolve(key),"synthetic");Files.setLastModifiedTime(directory.resolve(key),FileTime.from(now.minusSeconds(172800)));}
        Files.setLastModifiedTime(directory.resolve(recent),FileTime.from(now));when(repository.storedKeys()).thenReturn(List.of(known));
        var result=new AttachmentInspectionService(p,repository,access,Clock.fixed(now,ZoneOffset.UTC)).inspect(1L);
        assertEquals(List.of(orphan),result.candidates());assertFalse(result.truncated());verify(access).requireAdmin(1L);
        try(var files=Files.list(directory)){assertEquals(4,files.count());}
    }
    @Test void authorizationHappensBeforeFilesystemInspection(){var access=mock(NotificationContactService.class);doThrow(new org.springframework.security.access.AccessDeniedException("synthetic")).when(access).requireAdmin(2L);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->new AttachmentInspectionService(new AttachmentProperties(),mock(AttachmentRepository.class),access,Clock.systemUTC()).inspect(2L));}
}
