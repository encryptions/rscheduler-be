package com.rdmanager.rdb.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtHandler jwtHandler;

    public User createNewUser(String email, String password) {
        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) throw new IllegalArgumentException("A user with this email already exists.");

        String hashedPassword = passwordEncoder.encode(password);
        LocalDateTime timestamp = LocalDateTime.now();

        User user = new User();
        user.setEmail(email);
        user.setPassword(hashedPassword);
        user.setCreatedAt(timestamp);
        user.setUpdatedAt(timestamp);

        return userRepository.save(user);
    }
    
    public String authenticateUser(String email, String password) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
            	return jwtHandler.generateToken(email);
            } else {
                throw new IllegalArgumentException("Invalid credentials.");
            }
        } else {
            throw new IllegalArgumentException("User not found.");
        }
    }
    
    public String getRedditLogins(Integer userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isPresent()) {
            User existingUser = user.get();
            return existingUser.getRedditTokens();
        } else {
            throw new IllegalArgumentException("User not found.");
        }
    }
}
