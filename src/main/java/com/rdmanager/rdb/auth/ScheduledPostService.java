package com.rdmanager.rdb.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class ScheduledPostService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private JwtHandler jwtHandler;

    @PostMapping("/schedulepost")
    public ResponseEntity<String> schedulePost(@RequestBody Map<String, Object> postData, @RequestHeader("Authorization") String authorization) {
        String token = authorization.substring(7);
        String email = jwtHandler.extractEmail(token);
        Optional<User> cuser = userRepository.findByEmail(email);
        User user = cuser.orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found.");
        }
        try {
            String postType = "link";
            String body = (String) postData.get("body");
            if (body != null && !body.isEmpty()) {
                postType = "text";
            }
            Map<String, Object> processedPost = new HashMap<>(postData);
            processedPost.put("postType", postType);
            String scheduledPostJson = objectMapper.writeValueAsString(processedPost);
            String existingScheduledPosts = user.getScheduledPosts();
            if (existingScheduledPosts == null || existingScheduledPosts.isEmpty()) {
                existingScheduledPosts = scheduledPostJson;
            } else {
                existingScheduledPosts = existingScheduledPosts + "," + scheduledPostJson;
            }
            user.setScheduledPosts(existingScheduledPosts);
            userRepository.save(user);
            return ResponseEntity.ok("Post scheduled successfully!");
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing the post data.");
        }
    }
    
    @GetMapping("/getscheduledposts")
    public ResponseEntity<String> getScheduledPosts(@RequestHeader("Authorization") String authorization) {
        String token = authorization.substring(7);
        String email = jwtHandler.extractEmail(token);
        Optional<User> userOptional = userRepository.findByEmail(email);
        User user = userOptional.orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }
        String scheduledPosts = user.getScheduledPosts();
        return ResponseEntity.ok(scheduledPosts);
    }
}