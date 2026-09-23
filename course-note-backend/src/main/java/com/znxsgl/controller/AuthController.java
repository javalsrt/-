package com.znxsgl.controller;

import com.znxsgl.dto.LoginRequest;
import com.znxsgl.dto.LoginResponse;
import com.znxsgl.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
// ⚠️ 适配：本项目 context-path=/api，controller 映射基于应用内路径（servletPath，不含 /api 前缀）；
//    源项目未配 context-path，故原为 "/api/auth"。此处改为 "/auth"，对外实际 URL 仍是 /api/auth/...。
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        if (response.getToken() == null) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }
}