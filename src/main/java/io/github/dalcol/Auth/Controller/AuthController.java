package io.github.dalcol.Auth.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    @GetMapping("/auth")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        if (authentication.getPrincipal() instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("login", oauth2User.getAttribute("login"));
            userInfo.put("name", oauth2User.getAttribute("name"));
            userInfo.put("avatar_url", oauth2User.getAttribute("avatar_url"));
            return ResponseEntity.ok(userInfo);
        }

        return ResponseEntity.status(401).build();
    }
}
