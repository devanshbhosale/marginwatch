package com.devanshbhosale.marginwatch.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.devanshbhosale.marginwatch.domain.User;
import com.devanshbhosale.marginwatch.dto.LoginReq;
import com.devanshbhosale.marginwatch.dto.TokenRes;
import com.devanshbhosale.marginwatch.exception.ApiException;
import com.devanshbhosale.marginwatch.repository.UserRepository;
import com.devanshbhosale.marginwatch.security.JwtService;

import jakarta.validation.Valid;

/**
 * Login only — MarginWatch is an internal tool with no self-registration;
 * accounts exist exclusively through seeding.
 */
@RestController
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/api/auth/login")
    public TokenRes login(@Valid @RequestBody LoginReq request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return new TokenRes(jwtService.issue(user),
                new TokenRes.UserRes(user.getEmail(), user.getFullName(), user.getRole().name(), user.isDemo()));
    }
}
