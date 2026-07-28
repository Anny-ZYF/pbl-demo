package com.gdmu.storage;

import com.gdmu.common.ApiResponse;
import com.gdmu.common.BusinessException;
import com.gdmu.security.UserPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final StorageService storageService;
    private final StorageAccessMapper accessMapper;

    public FileController(StorageService storageService, StorageAccessMapper accessMapper) {
        this.storageService = storageService;
        this.accessMapper = accessMapper;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ApiResponse<StoredObject> upload(@AuthenticationPrincipal UserPrincipal user,
                                            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(storageService.store(file, user));
    }

    @GetMapping
    public ResponseEntity<Resource> download(@AuthenticationPrincipal UserPrincipal user,
                                             @org.springframework.web.bind.annotation.RequestParam("key") String key) {
        if (!canRead(user, key)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权读取该附件");
        }
        StoredResource stored = storageService.load(key);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(stored.filename(), java.nio.charset.StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .contentLength(stored.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(stored.resource());
    }

    private boolean canRead(UserPrincipal user, String key) {
        if (user == null || key == null) return false;
        if (user.role() == com.gdmu.security.Role.ADMIN || isOwnerKey(user, key)) return true;
        return switch (user.role()) {
            case TEACHER -> accessMapper.teacherCanRead(user.id(), key);
            case STUDENT -> accessMapper.studentCanRead(user.id(), key);
            case ADMIN -> true;
        };
    }

    private boolean isOwnerKey(UserPrincipal user, String key) {
        String normalized = key.replace('\\', '/');
        String[] parts = normalized.split("/");
        return parts.length == 5
                && parts[0].matches("\\d{4}")
                && parts[1].matches("\\d{2}")
                && parts[2].equals(user.role().name().toLowerCase(java.util.Locale.ROOT))
                && parts[3].equals(Long.toString(user.id()))
                && parts[4].matches("[0-9a-fA-F-]{36}\\.[A-Za-z0-9]{1,8}");
    }
}
