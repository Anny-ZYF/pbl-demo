package com.gdmu.storage;

import com.gdmu.security.UserPrincipal;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    StoredObject store(MultipartFile file, UserPrincipal owner);

    StoredResource load(String storageKey);
}
