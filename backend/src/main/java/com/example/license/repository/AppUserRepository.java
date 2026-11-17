
package com.example.license.repository;

import com.example.license.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByEmailAndApiKeyAndAppId(
            String email,
            String apiKey,
            String appId
    );
}
