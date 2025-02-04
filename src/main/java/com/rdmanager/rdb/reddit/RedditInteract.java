package com.rdmanager.rdb.reddit;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import com.rdmanager.rdb.TempStateCache;
import com.rdmanager.rdb.auth.JwtHandler;
import com.rdmanager.rdb.auth.User;
import com.rdmanager.rdb.auth.UserController;
import com.rdmanager.rdb.auth.UserRepository;
import com.rdmanager.rdb.auth.UserService;
import io.jsonwebtoken.JwtException;

@RestController
@CrossOrigin(origins = "REPLACE_WITH_FRONTEND_URL")
public class RedditInteract {
	@Autowired
    private JwtHandler jwtHandler;
	
	@Autowired
    private UserRepository userRepository;
	
	@Autowired
    private UserService userService;
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    
    @GetMapping(value = "/getauths")
    public ResponseEntity<?> getAuths(@RequestHeader("Authorization") String authHeader) {
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
            return ResponseEntity.ok(tokens);
        } catch (JwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid JWT");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
    
    @PostMapping(value = "/createnewauth")
    public ResponseEntity<?> createNewAuth(@RequestHeader("Authorization") String authHeader,@RequestBody String newToken) {
    	String email = jwtHandler.extractEmail(authHeader.substring(7));
    	Optional<User> user = userRepository.findByEmail(email);
    	User cuser = user.orElseThrow(()->new IllegalArgumentException());
    	if (newToken.startsWith(",")) {
            newToken = newToken.substring(1); // filtering cause it wasn't handling multiple removals properly
        }
    	if (cuser.getRedditTokens()!=null) {cuser.setRedditTokens(cuser.getRedditTokens()+","+newToken);}
    	else {cuser.setRedditTokens(newToken);}
    	userRepository.save(cuser);
    	return ResponseEntity.ok("Set!");        
    }
    
    @Autowired
    private TempStateCache tempStateCache;
    
    @PostMapping("/oauthredirect")
    public ResponseEntity<String> getRedditOAuthUrl(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or missing authorization header.");
        }

        String token = authHeader.substring(7);
        String email = jwtHandler.extractEmail(token);

        if (!jwtHandler.validateToken(token, email)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired token.");
        }

        String state = UUID.randomUUID().toString();
        tempStateCache.put(state, email);
        String redditOAuthUrl = "https://www.reddit.com/api/v1/authorize?" +
                "response_type=code&" +
                "client_id=REPLACE_WITH_REDDIT_APP_CLIENTID&" +
                "redirect_uri=REPLACE_WITH_API_URL/oauthcallback&" +
                "scope=submit,flair,identity&" +
                "state=" + state + "&" +
                "duration=permanent";

        return ResponseEntity.ok(redditOAuthUrl);
    }
    
    @GetMapping("/oauthcallback")
    public ResponseEntity<String> handleRedditCallback(@RequestParam("code") String code, @RequestParam("state") String state) {
        String email = tempStateCache.get(state);
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired state");
        }

        try {
            String redditAccessToken = getRedditAccessToken(code);
            String redditUsername = getRedditUsername(redditAccessToken);
            User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
            String newRedditTokenEntry = String.format("{%s:%s}", redditUsername, redditAccessToken);
            String updatedTokens = (user.getRedditTokens() == null) ?
                    newRedditTokenEntry :
                    user.getRedditTokens() + "," + newRedditTokenEntry;
            user.setRedditTokens(updatedTokens);
            userRepository.save(user);
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create("REPLACE_WITH_API_URL/accounts"));
            return ResponseEntity.status(HttpStatus.SEE_OTHER).headers(headers).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error handling Reddit callback: " + e.getMessage());
        }
    }

    private String getRedditAccessToken(String code) throws Exception {
        String clientId = "REPLACE_WITH_REDDIT_APP_CLIENTID";
        String clientSecret = "REPLACE_WITH_REDDIT_APP_CLIENTSECRET";
        String redirectUri = "REPLACE_WITH_API_URL/oauthcallback";
        String tokenUrl = "https://www.reddit.com/api/v1/access_token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);
        String body = "grant_type=authorization_code" +
                      "&code=" + code +
                      "&redirect_uri=" + redirectUri;
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, request,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            } else {
                throw new RuntimeException("Failed to get access token: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while fetching Reddit access token", e);
        }
    }

    private String getRedditUsername(String accessToken) {
        String apiUrl = "https://oauth.reddit.com/api/v1/me";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("User-Agent", "rScheduler/0.1 by (encryptions)");
        HttpEntity<Void> request = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    apiUrl, HttpMethod.GET, request,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("name");
            } else {
                throw new RuntimeException("Failed to fetch Reddit username: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while fetching Reddit username", e);
        }
    }
    
    


	private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email != null && email.matches(emailRegex);
    }
}
