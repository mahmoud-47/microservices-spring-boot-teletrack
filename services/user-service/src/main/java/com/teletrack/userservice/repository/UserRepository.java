package com.teletrack.userservice.repository;

import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByRole(UserRole role, Pageable pageable);

    List<User> findByActiveAndApproved(Boolean active, Boolean approved);

    Page<User> findByActiveAndApproved(Boolean active, Boolean approved, Pageable pageable);

    long countByRole(UserRole role);

    long countByActiveAndApproved(Boolean active, Boolean approved);
}
