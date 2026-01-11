package com.example.snippetshare.admin;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.snippetshare.entity.AdminEntity;
import com.example.snippetshare.entity.SnippetEntity;
import com.example.snippetshare.entity.UserEntity;
import com.example.snippetshare.repository.AdminJpaRepository;
import com.example.snippetshare.repository.SnippetJpaRepository;
import com.example.snippetshare.repository.UserJpaRepository;
import com.example.snippetshare.snippet.SnippetDto;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private SnippetJpaRepository snippetRepository;

    @Autowired
    private AdminJpaRepository adminRepository;

    private boolean isValidAdmin(String adminName, String password) {
        if (adminName == null || adminName.isBlank()) return false;
        return adminRepository.findByNameIgnoreCase(adminName)
                .filter(a -> password == null || password.isBlank() || a.getPassword().equals(password))
                .isPresent();
    }

    public record AdminLoginRequest(String name, String password) {}

    @PostMapping("/login")
    public ResponseEntity<AdminEntity> login(@RequestBody AdminLoginRequest request) {
        if (request == null || request.name() == null || request.password() == null) {
            return ResponseEntity.badRequest().build();
        }
        return adminRepository.findByNameIgnoreCase(request.name())
                .filter(a -> a.getPassword().equals(request.password()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers(@RequestHeader("X-Admin-Name") String adminName) {
        if (!isValidAdmin(adminName, null)) {
            return ResponseEntity.status(401).build();
        }
        List<UserEntity> users = userRepository.findAll();
        List<UserDto> userDtos = users.stream()
                .map(user -> {
                    long snippetCount = snippetRepository.countByUser(user);
                    return new UserDto(user.getId(), user.getName(), user.getCreatedAt(), snippetCount);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(userDtos);
    }

    @GetMapping("/snippets")
    public ResponseEntity<List<SnippetDto>> getAllSnippets(@RequestHeader("X-Admin-Name") String adminName) {
        if (!isValidAdmin(adminName, null)) {
            return ResponseEntity.status(401).build();
        }
        List<SnippetEntity> snippets = snippetRepository.findAllWithUser();
        List<SnippetDto> snippetDtos = snippets.stream()
                .map(snippet -> {
                    String userNameForSnippet = snippet.getUser() != null ? snippet.getUser().getName() : "Guest";
                    return SnippetDto.fromEntity(snippet, userNameForSnippet);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(snippetDtos);
    }

    @GetMapping("/snippets/search")
    public ResponseEntity<List<SnippetDto>> searchSnippets(@RequestParam("q") String query, @RequestHeader("X-Admin-Name") String adminName) {
        if (!isValidAdmin(adminName, null)) {
            return ResponseEntity.status(401).build();
        }
        List<SnippetEntity> snippets = snippetRepository.searchSnippetsWithUser(query);
        List<SnippetDto> snippetDtos = snippets.stream()
                .map(snippet -> {
                    String userNameForSnippet = snippet.getUser() != null ? snippet.getUser().getName() : "Guest";
                    return SnippetDto.fromEntity(snippet, userNameForSnippet);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(snippetDtos);
    }

    @GetMapping("/users/{userId}/snippets")
    public ResponseEntity<List<SnippetDto>> getUserSnippets(@PathVariable UUID userId, @RequestHeader("X-Admin-Name") String adminName) {
        if (!isValidAdmin(adminName, null)) {
            return ResponseEntity.status(401).build();
        }
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        
        List<SnippetEntity> snippets = snippetRepository.findByUserIdWithUser(userId);
        List<SnippetDto> snippetDtos = snippets.stream()
                .map(snippet -> SnippetDto.fromEntity(snippet, user.getName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(snippetDtos);
    }

    @DeleteMapping("/users/{userId}")
    @Transactional
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId, @RequestHeader("X-Admin-Name") String adminName) {
        try {
            if (!isValidAdmin(adminName, null)) {
                logger.warn("Unauthorized admin attempt to delete user: {}", adminName);
                return ResponseEntity.status(401).build();
            }
            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                logger.info("User with ID {} not found", userId);
                return ResponseEntity.notFound().build();
            }

            // Delete all snippets by this user first
            snippetRepository.deleteByUser(user);
            logger.info("Deleted all snippets for user with ID {}", userId);

            // Then delete the user
            userRepository.delete(user);
            logger.info("Deleted user with ID {}", userId);

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error occurred while deleting user with ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("/snippets/{snippetId}")
    public ResponseEntity<Void> deleteSnippet(@PathVariable UUID snippetId, @RequestHeader("X-Admin-Name") String adminName) {
        if (!isValidAdmin(adminName, null)) {
            return ResponseEntity.status(401).build();
        }
        SnippetEntity snippet = snippetRepository.findById(snippetId).orElse(null);
        if (snippet == null) {
            return ResponseEntity.notFound().build();
        }
        
        snippetRepository.delete(snippet);
        return ResponseEntity.noContent().build();
    }

    public static class UserDto {
        private UUID id;
        private String name;
        private java.time.Instant createdAt;
        private long snippetCount;

        public UserDto(UUID id, String name, java.time.Instant createdAt, long snippetCount) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
            this.snippetCount = snippetCount;
        }

        // Getters and setters
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public java.time.Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }
        
        public long getSnippetCount() { return snippetCount; }
        public void setSnippetCount(long snippetCount) { this.snippetCount = snippetCount; }
    }
}
