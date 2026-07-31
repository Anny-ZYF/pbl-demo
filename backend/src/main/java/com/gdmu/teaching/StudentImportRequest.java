package com.gdmu.teaching;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StudentImportRequest(@NotEmpty @Size(max = 500) List<@Size(max = 20) String> studentNos) { }
