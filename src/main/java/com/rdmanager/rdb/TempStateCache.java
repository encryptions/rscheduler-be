package com.rdmanager.rdb;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class TempStateCache {
    private ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public void put(String state, String email) {
        cache.put(state, email);
    }

    public String get(String state) {
        return cache.remove(state);
    }
}