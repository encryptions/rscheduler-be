package com.rdmanager.rdb.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@CrossOrigin(origins = "REPLACE_WITH_FRONTEND_URL")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<String> createUser(@RequestBody UserRequest userRequest) {
        try {
        	if (!isValidEmail(userRequest.getEmail())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid email format.");
            }
            User user = userService.createNewUser(userRequest.getEmail(), userRequest.getPassword());
            System.out.println("New user with email " + userRequest.getEmail() + " registered.");
            String token = userService.authenticateUser(userRequest.getEmail(), userRequest.getPassword());
            logger.info("User with email "+userRequest.getEmail()+" has successfully logged in.");
            return ResponseEntity.ok(token);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal server error. (unknown)");
        }
    }
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserRequest userRequest) {
        try {
            String email = userRequest.getEmail();
            String password = userRequest.getPassword();
            if (!isValidEmail(email)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid email format.");
            }
            String token = userService.authenticateUser(email, password);
            logger.info("User with email "+email+" has successfully logged in.");
            return ResponseEntity.ok(token);
        } catch (IllegalArgumentException e) {
            logger.error("Authentication error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error occurred during login: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtHandler jwtHandler;
    
    @PostMapping(value = "/deleteauth")
    public ResponseEntity<?> deleteAuth(
        @RequestHeader("Authorization") String authHeader, 
        @RequestBody String tokenToDelete
    ) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid Authorization header");
            }
            String token = authHeader.substring(7);
            String email = jwtHandler.extractEmail(token);
            if (!jwtHandler.validateToken(token, email)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired token");
            }
            Optional<User> ou = userRepository.findByEmail(email);
            User user = ou.orElseThrow(() -> new IllegalArgumentException("User not found"));
            String tokens = user.getRedditTokens();
            if (tokens == null || tokens.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No tokens to delete");
            }

            List<String> tokenList = new ArrayList<>(List.of(tokens.split(",")));
            if (!tokenList.remove(tokenToDelete)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Token not found");
            }
            user.setRedditTokens(String.join(",", tokenList));
            userRepository.save(user);
            return ResponseEntity.ok("Token deleted successfully");
        } catch (JwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid JWT");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
    
    @GetMapping("/getredditaccounts")
    public ResponseEntity<List<String>> getRedditAccounts(@RequestHeader("Authorization") String bearerToken) {
        try {
        	String authy = bearerToken.substring(7);
            String email = jwtHandler.extractEmail(authy);
            Optional<User> userOptional = userRepository.findByEmail(email);
            User user = userOptional.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            String redditTokens = user.getRedditTokens();
            if (redditTokens == null || redditTokens.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            String[] tokenEntries = redditTokens.split(",");
            List<String> usernames = new ArrayList<>();
            for (String entry : tokenEntries) {
                String username = entry.split(":")[0].replaceAll("[{}]", "");
                usernames.add(username);
            }
            return ResponseEntity.ok(usernames);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Collections.singletonList("Error: " + e.getMessage()));
        }
    }
    
    @PostMapping("/deletescheduledpost")
    public ResponseEntity<String> deleteScheduledPost(@RequestHeader("Authorization") String authorization, @RequestBody String postToDelete) {
        String token = authorization.substring(7);
        String email = jwtHandler.extractEmail(token);
        Optional<User> userOptional = userRepository.findByEmail(email);
        User user = userOptional.orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }
        String scheduledPosts = user.getScheduledPosts();
        List<String> postList = splitJsonObjects(scheduledPosts);
        postList.removeIf(post -> post.equals(postToDelete));
        String updatedPosts = String.join(",", postList);
        user.setScheduledPosts(updatedPosts);
        userRepository.save(user);

        return ResponseEntity.ok("Post deleted successfully");
    }

    private List<String> splitJsonObjects(String text) {
        List<String> objects = new ArrayList<>();
        int balance = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                if (balance == 0) {
                    start = i;
                }
                balance++;
            } else if (c == '}') {
                balance--;
                if (balance == 0) {
                    objects.add(text.substring(start, i + 1));
                }
            }
        }
        return objects;
    }
    
    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email != null && email.matches(emailRegex);
    }
}