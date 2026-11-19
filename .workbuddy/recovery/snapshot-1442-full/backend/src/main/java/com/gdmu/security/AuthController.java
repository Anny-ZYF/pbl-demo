package com.gdmu.security;

import com.gdmu.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserPrincipal user) {
        authService.logout(user);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(authService.me(user));
    }

    @PutMapping("/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal UserPrincipal user,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user, request);
        return ApiResponse.ok(null);
    }
}
