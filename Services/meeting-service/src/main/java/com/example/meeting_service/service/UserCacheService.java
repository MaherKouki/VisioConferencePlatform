package com.example.meeting_service.service;


import com.example.meeting_service.client.AuthServiceClient;
import com.example.meeting_service.dto.UserInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCacheService {
    private final AuthServiceClient authServiceClient;

    @Cacheable(value = "userInfo" , key = "#userId")
    public UserInfo getUserInfo(String userId) {

        log.debug("Cache for user {} , fetching from auth Service", userId);

        UserInfo userInfo = authServiceClient.getUserInfo(userId);

        if (userInfo != null) {
            log.info("User {} cached for 15 minutes", userId);
        }
        else {
            log.warn("User {} not found in auth Service", userId);
        }

        return userInfo;
    }


    @Cacheable(value = "userExists" , key = "#userId")
    public boolean userExists(String userId) {
        log.debug("Cache for user {} , fetching from auth Service", userId);
        boolean exits = authServiceClient.userExists(userId);
        log.info("User {} exists , chached : {}", userId ,  exits);
        return exits;
    }


    @CacheEvict(value = "userInfo", key = "#userId")
    public void evictUserCache(String userId) {
        log.info("Cache evicted for user {}", userId);
    }


    @CacheEvict(value = "userInfo", allEntries = true)
    public void evictAllUserCache() {
        log.warn("All user cache evicted");
    }

}
