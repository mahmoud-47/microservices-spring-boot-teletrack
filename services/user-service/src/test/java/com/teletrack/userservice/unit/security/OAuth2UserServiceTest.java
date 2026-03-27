package com.teletrack.userservice.unit.security;

import com.teletrack.userservice.entity.RefreshToken;
import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.repository.RefreshTokenRepository;
import com.teletrack.userservice.repository.UserRepository;
import com.teletrack.userservice.security.JwtService;
import com.teletrack.userservice.security.OAuth2UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("OAuth2UserService Unit Tests")
class OAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private OAuth2UserService oAuth2UserService;

    private OAuth2User buildOAuth2User(String email, String firstName, String lastName) {
        OAuth2User oAuth2User = mock(OAuth2User.class);
        lenient().when(oAuth2User.getAttribute("email")).thenReturn(email);
        lenient().when(oAuth2User.getAttribute("given_name")).thenReturn(firstName);
        lenient().when(oAuth2User.getAttribute("family_name")).thenReturn(lastName);
        return oAuth2User;
    }

    @Test
    @DisplayName("Should create new user with active=true and approved=false for new OAuth user")
    void processOAuth2User_NewUser_CreatesUserWithCorrectDefaults() {
        OAuth2User oAuth2User = buildOAuth2User("new@example.com", "Jane", "Doe");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = oAuth2UserService.processOAuth2User(oAuth2User, "OPERATOR");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getFirstName()).isEqualTo("Jane");
        assertThat(saved.getLastName()).isEqualTo("Doe");
        assertThat(saved.getRole()).isEqualTo(UserRole.OPERATOR);
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getApproved()).isFalse();
        assertThat(saved.getPasswordHash()).isNull();
    }

    @Test
    @DisplayName("Should update name for existing OAuth user when name differs")
    void processOAuth2User_ExistingUser_UpdatesNameIfDifferent() {
        User existing = User.builder()
                .id(UUID.randomUUID())
                .email("existing@example.com")
                .firstName("OldFirst")
                .lastName("OldLast")
                .role(UserRole.OPERATOR)
                .active(true)
                .approved(true)
                .build();

        OAuth2User oAuth2User = buildOAuth2User("existing@example.com", "NewFirst", "NewLast");
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        oAuth2UserService.processOAuth2User(oAuth2User, "OPERATOR");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFirstName()).isEqualTo("NewFirst");
        assertThat(captor.getValue().getLastName()).isEqualTo("NewLast");
    }

    @Test
    @DisplayName("Should activate existing user if inactive")
    void processOAuth2User_ExistingUser_ActivatesIfInactive() {
        User existing = User.builder()
                .id(UUID.randomUUID())
                .email("inactive@example.com")
                .firstName("John")
                .lastName("Doe")
                .role(UserRole.OPERATOR)
                .active(false)
                .approved(false)
                .build();

        OAuth2User oAuth2User = buildOAuth2User("inactive@example.com", "John", "Doe");
        when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        oAuth2UserService.processOAuth2User(oAuth2User, "OPERATOR");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    @DisplayName("Should throw RuntimeException when email is null from OAuth2 provider")
    void processOAuth2User_NullEmail_ThrowsRuntimeException() {
        OAuth2User oAuth2User = buildOAuth2User(null, "Jane", "Doe");

        assertThatThrownBy(() -> oAuth2UserService.processOAuth2User(oAuth2User, "OPERATOR"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email not found from OAuth2 provider");
    }

    @Test
    @DisplayName("Should default firstName to 'User' when given_name is null")
    void processOAuth2User_NullFirstName_DefaultsToUser() {
        OAuth2User oAuth2User = buildOAuth2User("noname@example.com", null, null);
        when(userRepository.findByEmail("noname@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        oAuth2UserService.processOAuth2User(oAuth2User, "SUPPORT");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFirstName()).isEqualTo("User");
        assertThat(captor.getValue().getLastName()).isEqualTo("");
    }

    @Test
    @DisplayName("Should create and save refresh token for user")
    void createRefreshToken_SavesAndReturnsToken() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .role(UserRole.OPERATOR)
                .active(true)
                .build();

        when(jwtService.getRefreshTokenExpiration()).thenReturn(604_800_000L);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String token = oAuth2UserService.createRefreshToken(user);

        assertThat(token).isNotNull().isNotBlank();
        // Should be parseable as UUID
        assertThat(UUID.fromString(token)).isNotNull();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().isUsed()).isFalse();
        assertThat(captor.getValue().isRevoked()).isFalse();
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }
}
