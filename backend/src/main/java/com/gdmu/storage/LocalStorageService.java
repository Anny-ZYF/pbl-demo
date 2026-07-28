package com.gdmu.storage;

import com.gdmu.common.BusinessException;
import com.gdmu.security.UserPrincipal;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip", "txt", "md", "png", "jpg", "jpeg");
    private final Path root;

    public LocalStorageService(StorageProperties properties) {
        this.root = properties.root().toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() throws IOException {
        Files.createDirectories(root);
    }

    @Override
    public StoredObject store(MultipartFile file, UserPrincipal owner) {
        if (file.isEmpty() || file.getOriginalFilename() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "上传文件为空");
        }
        String originalName = Path.of(file.getOriginalFilename()).getFileName().toString();
        String extension = extension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持该文件类型");
        }
        LocalDate today = LocalDate.now();
        String key = "%d/%02d/%s/%d/%s.%s".formatted(today.getYear(), today.getMonthValue(),
                owner.role().name().toLowerCase(Locale.ROOT), owner.id(), UUID.randomUUID(), extension);
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件路径不合法");
        }
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream source = file.getInputStream(); DigestInputStream input = new DigestInputStream(source, digest)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredObject(key.replace('\\', '/'), originalName, file.getSize(),
                    HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败");
        }
    }

    @Override
    public StoredResource load(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.length() > 500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件标识不合法");
        }
        Path target = root.resolve(storageKey.replace('/', java.io.File.separatorChar)).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        try {
            String contentType = Files.probeContentType(target);
            return new StoredResource(new FileSystemResource(target), target.getFileName().toString(),
                    Files.size(target), contentType == null ? "application/octet-stream" : contentType);
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "文件读取失败");
        }
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
