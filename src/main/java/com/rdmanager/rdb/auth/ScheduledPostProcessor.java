package com.rdmanager.rdb.auth;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ScheduledPostProcessor {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String REDDIT_API_BASE_URL = "https://oauth.reddit.com/api/submit";
    private UserController userController;

    public ScheduledPostProcessor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Scheduled(fixedRate = 15000)
    public void runScheduler() {
        processAllScheduledPosts();
    }
    
    public void deleteScheduledPostForUser(User user, String postJson) {
        String scheduledPosts = user.getScheduledPosts();
        List<String> postList = new ArrayList<>();
        StringBuilder currentPost = new StringBuilder();
        int openBrackets = 0;
        for (int i = 0; i < scheduledPosts.length(); i++) {
            char currentChar = scheduledPosts.charAt(i);
            currentPost.append(currentChar);
            if (currentChar == '{') {
                openBrackets++;
            } else if (currentChar == '}') {
                openBrackets--;
            }
            if (openBrackets == 0) {
                postList.add(currentPost.toString().trim());
                currentPost = new StringBuilder();
            }
        }
        postList.removeIf(post -> post.equals(postJson));
        if (postList.isEmpty()) {
            user.setScheduledPosts("");
            userRepository.save(user);
            return;
        }
        postList = postList.stream().filter(post -> !post.isEmpty()).collect(Collectors.toList());
        String updatedPosts = String.join(",", postList);

        updatedPosts = updatedPosts.replaceAll("(?<=,)\s*,", "")
                .replaceAll("^,", "")
                .replaceAll(",$", "");
        if (updatedPosts.isEmpty()) {
            updatedPosts = "";
        }
        System.out.println("Updated posts: " + updatedPosts);
        user.setScheduledPosts(updatedPosts);
        userRepository.save(user);
    }
    
    public void processAllScheduledPosts() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
        	String posts = user.getScheduledPosts();
        	if (posts == null || posts.isEmpty() || posts.isBlank()) {continue;}
        	else {
        		String[] postArray = posts.split("(?<=\\}),\\{");
        		for (int i = 0; i < postArray.length; i++) {
        	        String postJson = postArray[i];
        	        if (!postJson.startsWith("{")) {
        	            postJson = "{" + postJson;
        	        }
        	        if (!postJson.endsWith("}")) {
        	            postJson = postJson + "}";
        	        }
        	        try {
        	            Map<String, Object> post = new ObjectMapper().readValue(postJson, new TypeReference<Map<String, Object>>() {});
        	            String activetoken = extractRedditToken(user.getRedditTokens(), post.get("redditAccount").toString());
        	            LocalDateTime postDate = LocalDateTime.parse(post.get("postDate").toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        	            if (!postDate.isAfter(LocalDateTime.now())) {
        	                System.out.println("ding");
        	                boolean postSuccess = postToReddit(post, activetoken);
        	                if (postSuccess) {
        	                    System.out.println("Post successfully published to Reddit.");
        	                    deleteScheduledPostForUser(user, postJson);
        	                } else {
        	                    System.out.println("Failed to publish post to Reddit.");
        	                }
        	            }
        	        } catch (Exception e) {
        	            e.printStackTrace();
        	        }

        	    }
        	}
        }
    }

    private boolean postToReddit(Map<String, Object> post, String redditToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + redditToken);
        headers.set("Content-Type", "application/x-www-form-urlencoded");
        headers.set("User-Agent", "rScheduler/1.0.0");

        StringBuilder requestBody = new StringBuilder()
        	    .append("kind=").append(post.get("isLinkPost") != null && (Boolean) post.get("isLinkPost") ? "link" : "self")
        	    .append("&sr=").append(post.get("subreddit"))
        	    .append("&title=").append(post.get("title"))
        	    .append("&nsfw=").append(post.get("nsfw"))
        	    .append("&spoiler=").append(post.get("spoiler"));

    	if (post.get("isLinkPost") != null && (Boolean) post.get("isLinkPost")) {
    	    requestBody.append("&url=").append(post.get("imageUrl"));
    	} else {
    	    requestBody.append("&text=").append(post.get("body") != null ? post.get("body") : "");
    	}

        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(REDDIT_API_BASE_URL, HttpMethod.POST, entity, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String extractRedditToken(String redditTokens, String redditAccount) {
        if (redditTokens == null || redditTokens.isEmpty()) {
            return null;
        }
        String[] tokenPairs = redditTokens.split("\\},\\{");
        tokenPairs[0] = tokenPairs[0].replaceFirst("^\\{", "");
        tokenPairs[tokenPairs.length - 1] = tokenPairs[tokenPairs.length - 1].replaceFirst("\\}$", "");
        for (String pair : tokenPairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2 && keyValue[0].equals(redditAccount)) {
                return keyValue[1];
            }
        }

        return null;
    }
}