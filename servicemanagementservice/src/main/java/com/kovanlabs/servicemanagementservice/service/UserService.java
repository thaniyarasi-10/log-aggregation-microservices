package com.kovanlabs.servicemanagementservice.service;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.kovanlabs.servicemanagementservice.model.AppUser;
import com.kovanlabs.servicemanagementservice.repository.AppUserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UserService {

    private final AppUserRepository appUserRepository;
    private final S3Service s3Service;

    public UserService(AppUserRepository appUserRepository, S3Service s3Service) {
        this.appUserRepository = appUserRepository;
        this.s3Service = s3Service;
    }

    @Transactional(readOnly = true)
    public AppUser getUserByEmail(String email) {
        return appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional
    public String uploadProfileImage(String userId, MultipartFile file) {
        log.info("Uploading profile image for user ID: {}", userId);
        
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        String imageUrl;
        try {
            imageUrl = s3Service.uploadProfileImage(file);
        } catch (IOException e) {
            log.error("S3 upload failed for user: {}", userId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload image: " + e.getMessage());
        }
        
        user.setProfileImageUrl(imageUrl);
        appUserRepository.save(user);
        return imageUrl;
    }

    @Transactional
    public AppUser updateProfileImage(String email, String imageUrl) {
        log.info("Updating profile image URL for user: {}, url={}", email, imageUrl);
        AppUser user = appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        user.setProfileImageUrl(imageUrl);
        return appUserRepository.save(user);
    }
}
