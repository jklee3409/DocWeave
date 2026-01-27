package com.docweave.server.auth.controller;

import com.docweave.server.auth.dto.common.CustomUserDetailsDto;
import com.docweave.server.auth.dto.request.LoginRequestDto;
import com.docweave.server.auth.dto.request.RefreshTokenRequestDto;
import com.docweave.server.auth.dto.request.SignupRequestDto;
import com.docweave.server.auth.dto.response.TokenResponseDto;
import com.docweave.server.auth.service.AuthService;
import com.docweave.server.common.dto.BaseResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public BaseResponseDto<TokenResponseDto> signup(@RequestBody SignupRequestDto request) {
        TokenResponseDto response = authService.signup(request);
        return BaseResponseDto.success(response);
    }

    @PostMapping("/login")
    public BaseResponseDto<TokenResponseDto> login(@RequestBody LoginRequestDto request) {
        TokenResponseDto response = authService.login(request);
        return BaseResponseDto.success(response);
    }

    @PostMapping("/logout")
    public BaseResponseDto<Void> logout(
            @AuthenticationPrincipal CustomUserDetailsDto customUserDetailsDto,
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.substring(7);
        authService.logout(customUserDetailsDto.getId(), token);
        return BaseResponseDto.voidSuccess();
    }

    @PostMapping("/refresh")
    public BaseResponseDto<TokenResponseDto> refresh(@RequestBody RefreshTokenRequestDto request) {
        TokenResponseDto response = authService.refreshToken(request.getRefreshToken());
        return BaseResponseDto.success(response);
    }
}
