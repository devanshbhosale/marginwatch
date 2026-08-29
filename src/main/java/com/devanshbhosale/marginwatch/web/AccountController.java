package com.devanshbhosale.marginwatch.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devanshbhosale.marginwatch.domain.User;
import com.devanshbhosale.marginwatch.dto.AccountRes;

@RestController
public class AccountController {

    @GetMapping("/api/account")
    public AccountRes account(@AuthenticationPrincipal User user) {
        return new AccountRes(user.getEmail(), user.getFullName(), user.getRole().name(), user.isDemo());
    }
}
