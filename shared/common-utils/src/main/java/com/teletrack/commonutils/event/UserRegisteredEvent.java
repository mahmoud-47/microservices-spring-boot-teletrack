package com.teletrack.commonutils.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserRegisteredEvent extends UserEvent {
    private static final long serialVersionUID = 1L;

    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String verificationToken;
}