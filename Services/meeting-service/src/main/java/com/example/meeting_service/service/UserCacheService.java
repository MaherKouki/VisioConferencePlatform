package com.example.meeting_service.service;


import com.example.meeting_service.client.AuthServiceClient;
import com.example.meeting_service.dto.UserInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCacheService {
    private final AuthServiceClient authServiceClient;

    /*@Cacheable(value = "userInfo" , key = "#userId")
    public UserInfo getUserInfo(String userId) {

        log.debug("Cache for user , fetching from auth Service", userId);

        UserInfo userInfo = authServiceClient.getUserInfo(userId);

        if (userInfo == null) {
            log.info("User {} cached for 15 minutes", userId);
        }


    }*/


}
