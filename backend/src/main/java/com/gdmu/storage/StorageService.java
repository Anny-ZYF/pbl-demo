package com.gdmu.storage;

import com.gdmu.security.UserPrincipal;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    default StoredObject store(MultipartFile file, UserPrincipal owner) {
        return store(file, owner, null);
    }

    /**
     * @param originalNameOverride 由客户端以 ASCII 编码显式提供的原始文件名；
     *                              为空时回退到 multipart 头里的文件名。
     */
    StoredObject store(MultipartFile file, UserPrincipal owner, String originalNameOverride);

    StoredResource load(String storageKey);
}
