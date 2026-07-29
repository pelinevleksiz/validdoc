package com.validdoc.repository;

import com.validdoc.model.User;
import com.validdoc.model.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndActiveTrue(String username);

    long countByRole(UserRole role);

    long countByRoleAndActiveTrue(UserRole role);

    Page<User> findByActiveTrue(Pageable pageable);
}