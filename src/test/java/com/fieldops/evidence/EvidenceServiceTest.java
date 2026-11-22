package com.fieldops.evidence;

import com.fieldops.audit.application.AuditService;
import com.fieldops.evidence.application.EvidenceService;
import com.fieldops.evidence.application.EvidenceStorage;
import com.fieldops.evidence.domain.Evidence;
import com.fieldops.evidence.repository.EvidenceRepository;
import com.fieldops.inspection.domain.Inspection;
import com.fieldops.inspection.domain.InspectionStatus;
import com.fieldops.inspection.repository.InspectionResponseRepository;
import com.fieldops.review.repository.NonConformityRepository;
import com.fieldops.user.domain.Role;
import com.fieldops.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

    @Mock EvidenceRepository evidenceRepository;
    @Mock InspectionResponseRepository responseRepository;
    @Mock EvidenceStorage evidenceStorage;
    @Mock AuditService auditService;
    @Mock NonConformityRepository nonConformityRepository;

    private EvidenceService service;
    private Inspection inspection;
    private User technician;

    @BeforeEach
    void setUp() {
        service = new EvidenceService(evidenceRepository, responseRepository, evidenceStorage, auditService,
                nonConformityRepository);
        technician = new User();
        technician.setId(UUID.randomUUID());
        technician.setRole(Role.TECHNICIAN);
        inspection = new Inspection();
        inspection.setId(UUID.randomUUID());
        inspection.setStatus(InspectionStatus.IN_PROGRESS);
        inspection.setTechnician(technician);
        lenient().when(evidenceRepository.countByInspectionId(inspection.getId())).thenReturn(0L);
    }

    @Test
    void acceptsSupportedBinaryAndStoresDetectedMime() {
        MockMultipartFile file = file("photo.JPG", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01});
        when(evidenceStorage.store(inspection.getId(), file)).thenReturn(new EvidenceStorage.StoredFile("key", 4, "checksum"));
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Evidence evidence = service.upload(inspection, null, null, null, null, null, file, technician);

        assertEquals("image/jpeg", evidence.getMimeType());
        verify(evidenceStorage).store(inspection.getId(), file);
    }

    @Test
    void rejectsSpoofedContentAndDoesNotCallStorage() {
        MockMultipartFile file = file("photo.jpg", "image/jpeg", "not-an-image".getBytes());

        var error = assertThrows(RuntimeException.class,
                () -> service.upload(inspection, null, null, null, null, null, file, technician));

        assertEquals("INVALID_FILE_CONTENT", ((com.fieldops.shared.exception.BusinessRuleException) error).getRuleCode());
        verifyNoInteractions(evidenceStorage);
    }

    @Test
    void rejectsUnsupportedExtensionAndMoreThanTenFiles() {
        MockMultipartFile pdf = file("video.webp", "video/webm", new byte[]{1, 2, 3});
        var typeError = assertThrows(com.fieldops.shared.exception.BusinessRuleException.class,
                () -> service.upload(inspection, null, null, null, null, null, pdf, technician));
        assertEquals("UNSUPPORTED_FILE_TYPE", typeError.getRuleCode());

        when(evidenceRepository.countByInspectionId(inspection.getId())).thenReturn(10L);
        MockMultipartFile image = file("photo.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        var limitError = assertThrows(com.fieldops.shared.exception.BusinessRuleException.class,
                () -> service.upload(inspection, null, null, null, null, null, image, technician));
        assertEquals("EVIDENCE_LIMIT_EXCEEDED", limitError.getRuleCode());
    }

    @Test
    void rejectsFilesOverTenMegabytesBeforeStorage() {
        MockMultipartFile file = file("photo.jpg", "image/jpeg", new byte[(int) EvidenceService.MAX_FILE_SIZE_BYTES + 1]);

        var error = assertThrows(com.fieldops.shared.exception.BusinessRuleException.class,
                () -> service.upload(inspection, null, null, null, null, null, file, technician));

        assertEquals("FILE_TOO_LARGE", error.getRuleCode());
        verifyNoInteractions(evidenceStorage);
    }

    @Test
    void compensatesStorageWhenDatabasePersistenceFails() {
        MockMultipartFile file = file("photo.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        when(evidenceStorage.store(inspection.getId(), file)).thenReturn(new EvidenceStorage.StoredFile("key", 8, "checksum"));
        when(evidenceRepository.save(any(Evidence.class))).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> service.upload(inspection, null, null, null, null, null, file, technician));
        verify(evidenceStorage).delete("key");
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }
}
