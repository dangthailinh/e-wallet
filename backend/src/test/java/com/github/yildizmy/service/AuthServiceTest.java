package com.github.yildizmy.service;

import com.github.yildizmy.auth.RefreshSessionService;
import com.github.yildizmy.config.MessageSourceConfig;
import com.github.yildizmy.domain.entity.User;
import com.github.yildizmy.dto.mapper.SignupRequestMapper;
import com.github.yildizmy.dto.request.LoginRequest;
import com.github.yildizmy.dto.request.SignupRequest;
import com.github.yildizmy.exception.ElementAlreadyExistsException;
import com.github.yildizmy.repository.UserRepository;
import com.github.yildizmy.security.JwtUtils;
import com.github.yildizmy.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RefreshSessionService refreshSessionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SignupRequestMapper signupRequestMapper;

    @Mock
    private MessageSourceConfig messageConfig;

    private LoginRequest loginRequest;
    private UserDetailsImpl userDetails;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        loginRequest = new LoginRequest("testuser", " password ");
        userDetails = new UserDetailsImpl(
                1L,
                "testuser",
                "password",
                "Test",
                "User",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }

    @Test
    void loginShouldIssueBothTokensAndReturnSanitizedUserData() {
        User user = new User();
        user.setId(1L);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(refreshSessionService.issue(user)).thenReturn(new RefreshSessionService.RefreshToken(
                "opaque-refresh",
                Instant.parse("2030-01-01T00:00:00Z"),
                1L
        ));
        when(jwtUtils.issueAccessToken(authentication)).thenReturn(new JwtUtils.AccessToken(
                "test.jwt.token",
                Instant.parse("2030-01-01T00:00:00Z")
        ));

        var response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("test.jwt.token", response.accessToken().value());
        assertEquals("opaque-refresh", response.refreshToken().value());
        assertEquals(1L, response.user().id());
        assertEquals("testuser", response.user().username());
        assertEquals("Test", response.user().firstName());
        assertEquals("User", response.user().lastName());
        assertEquals(Collections.singletonList("ROLE_USER"), response.user().roles());
        assertFalse(response.toString().contains("test.jwt.token"));
        assertFalse(response.toString().contains("opaque-refresh"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> credentials =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(credentials.capture());
        assertEquals(" password ", credentials.getValue().getCredentials());
        verify(refreshSessionService).issue(user);
        verify(jwtUtils).issueAccessToken(authentication);
    }

    @Test
    void refreshRotatesRefreshTokenAndIssuesAccessTokenForItsOwner() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("encoded-password");
        user.setFirstName("Test");
        user.setLastName("User");
        RefreshSessionService.RefreshToken refreshToken = new RefreshSessionService.RefreshToken(
                "rotated-refresh",
                Instant.parse("2030-01-01T00:00:00Z"),
                1L
        );
        when(refreshSessionService.rotate("presented-refresh")).thenReturn(refreshToken);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtUtils.issueAccessToken(any(Authentication.class))).thenReturn(new JwtUtils.AccessToken(
                "new-access",
                Instant.parse("2030-01-01T00:00:00Z")
        ));

        var response = authService.refresh("presented-refresh");

        assertEquals("testuser", response.user().username());
        assertEquals("new-access", response.accessToken().value());
        assertSame(refreshToken, response.refreshToken());
    }

    @Test
    void logoutRevokesPresentedRefreshToken() {
        authService.logout("refresh-to-revoke");

        verify(refreshSessionService).revoke("refresh-to-revoke");
    }

    @Test
    void signup_shouldCreateNewUser() {
        var signupRequest = new SignupRequest(
                "New",
                "User",
                "newuser",
                "new@example.com",
                "password"
        );
        var newUser = new User();
        newUser.setId(2L);
        newUser.setUsername("newuser");

        when(userRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(signupRequestMapper.toUser(signupRequest)).thenReturn(newUser);
        when(userRepository.save(newUser)).thenReturn(newUser);

        var response = authService.signup(signupRequest);

        assertNotNull(response);
        assertEquals(2L, response.id());
        assertEquals(1, newUser.getWallets().size());
        var wallet = newUser.getWallets().iterator().next();
        assertSame(newUser, wallet.getUser());
        assertEquals(0, wallet.getBalance().signum());
        assertEquals("Main wallet", wallet.getName());
        assertTrue(wallet.getIban().matches("EW[0-9a-f]{32}"));

        verify(userRepository).existsByUsernameIgnoreCase("newuser");
        verify(userRepository).existsByEmailIgnoreCase("new@example.com");
        verify(signupRequestMapper).toUser(signupRequest);
        verify(userRepository).save(newUser);
    }

    @Test
    void signup_shouldThrowExceptionWhenUsernameExists() {
        var signupRequest = new SignupRequest(
                "Existing",
                "User",
                "existinguser",
                "existing@example.com",
                "password"
        );

        when(userRepository.existsByUsernameIgnoreCase("existinguser")).thenReturn(true);

        assertThrows(ElementAlreadyExistsException.class, () -> authService.signup(signupRequest));

        verify(userRepository).existsByUsernameIgnoreCase("existinguser");
        verify(userRepository, never()).existsByEmailIgnoreCase(anyString());
        verify(signupRequestMapper, never()).toUser(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void signup_shouldThrowExceptionWhenEmailExists() {
        var signupRequest = new SignupRequest(
                "New",
                "User",
                "newuser",
                "existing@example.com",
                "password"
        );

        when(userRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        assertThrows(ElementAlreadyExistsException.class, () -> authService.signup(signupRequest));

        verify(userRepository).existsByUsernameIgnoreCase("newuser");
        verify(userRepository).existsByEmailIgnoreCase("existing@example.com");
        verify(signupRequestMapper, never()).toUser(any());
        verify(userRepository, never()).save(any());
    }
}
