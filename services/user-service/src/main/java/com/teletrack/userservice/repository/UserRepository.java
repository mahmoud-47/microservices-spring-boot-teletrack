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

    List<User> findByRole(UserRole role);

    @Query("SELECT u FROM User u WHERE u.active = :isActive AND u.approved = :isApproved")
    Page<User> findByIsActiveAndIsApproved(
            @Param("isActive") Boolean isActive,
            @Param("isApproved") Boolean isApproved,
            Pageable pageable
    );

    @Query("SELECT u FROM User u WHERE u.role = :role AND u.active = true AND u.approved = true")
    List<User> findActiveApprovedUsersByRole(@Param("role") UserRole role);

    @Query("SELECT u FROM User u WHERE u.approved = false")
    List<User> findPendingApprovalUsers();

    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);
}
