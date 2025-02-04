package com.rdmanager.rdb;
import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
        .authorizeRequests()
        .requestMatchers("/login", "/register", "/getauths", "/createnewauth", "/deleteauth", "/oauthredirect", "/oauthcallback", "/schedulepost"
        		, "/getredditaccounts", "/getscheduledposts", "/deletescheduledpost").permitAll()
        .anyRequest().authenticated()
        .and()
        .cors();

    return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
    	    CorsConfiguration configuration = new CorsConfiguration();
    	    configuration.setAllowedOrigins(Arrays.asList("REPLACE_WITH_FRONTEND_URL"));
    	    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    	    configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
    	    configuration.setExposedHeaders(Arrays.asList("Location"));
    	    configuration.setAllowCredentials(true);
    	    
    	    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    	    source.registerCorsConfiguration("/**", configuration);
    	    return source;
    }
}