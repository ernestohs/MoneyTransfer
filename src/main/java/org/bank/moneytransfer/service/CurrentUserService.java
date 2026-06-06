package org.bank.moneytransfer.service;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CurrentUserService {
    public String ownerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous-owner";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getClaimAsString("tenant_id"))
                    .filter(StringUtils::hasText)
                    .orElse(jwt.getSubject());
        }
        return Optional.ofNullable(authentication.getName()).filter(StringUtils::hasText).orElse("anonymous-owner");
    }

    public String actorId() {
        return ownerId();
    }
}
