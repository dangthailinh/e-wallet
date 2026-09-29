package com.github.yildizmy.service;

import com.github.yildizmy.auth.RefreshSessionService;
import com.github.yildizmy.config.MessageSourceConfig;
import com.github.yildizmy.domain.entity.User;
import com.github.yildizmy.domain.entity.Wallet;
import com.github.yildizmy.dto.mapper.SignupRequestMapper;
import com.github.yildizmy.dto.request.LoginRequest;
import com.github.yildizmy.dto.request.SignupRequest;
import com.github.yildizmy.dto.response.AuthSessionResponse;
import com.github.yildizmy.dto.response.CommandResponse;
import com.github.yildizmy.exception.ElementAlreadyExistsException;
import com.github.yildizmy.repository.UserRepository;
import com.github.yildizmy.security.JwtUtils;
import com.github.yildizmy.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.math.BigDecimal;

import static com.github.yildizmy.common.MessageKeys.*;

/**
 * Service used for Authentication related operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final MessageSourceConfig messageConfig;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final RefreshSessionService refreshSessionService;
    private final UserRepository userRepository;
    private final SignupRequestMapper signupRequestMapper;

    /**
     * Authenticates users by their credentials.
     *
     * @param request
     * @return authenticated session with credentials for transport cookies
     */
    @Transactional
    public AuthenticatedSession login(LoginRequest request) {
        final Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername().trim(), request.getPassword()));

        final UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(AuthService::invalidCredentials);
        RefreshSessionService.RefreshToken refreshToken = refreshSessionService.issue(user);

        log.info(messageConfig.getMessage(INFO_USER_LOGIN, userDetails.getUsername()));
        return authenticatedSession(authentication, refreshToken);
    }

    @Transactional(readOnly = true)
    public AuthenticatedSession refresh(String rawRefreshToken) {
        RefreshSessionService.RefreshToken refreshToken = refreshSessionService.rotate(rawRefreshToken);
        User user = userRepository.findById(refreshToken.userId())
                .orElseThrow(AuthService::invalidCredentials);
        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
        return authenticatedSession(authentication, refreshToken);
    }

    public void logout(String rawRefreshToken) {
        refreshSessionService.revoke(rawRefreshToken);
    }

    public AuthSessionResponse currentUser(UserDetailsImpl userDetails) {
        return toResponse(userDetails);
    }

    /**
     * Registers a user by provided credentials and user info.
     *
     * @param request
     * @return id of the registered user
     */
    @Transactional
    public CommandResponse signup(SignupRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.getUsername().trim()))
            throw new ElementAlreadyExistsException(messageConfig.getMessage(ERROR_USERNAME_EXISTS));
        if (userRepository.existsByEmailIgnoreCase(request.getEmail().trim()))
            throw new ElementAlreadyExistsException(messageConfig.getMessage(ERROR_EMAIL_EXISTS));

        final User user = signupRequestMapper.toUser(request);
        Wallet wallet = new Wallet();
        wallet.setIban("EW" + UUID.randomUUID().toString().replace("-", ""));
        wallet.setName("Main wallet");
        wallet.setBalance(BigDecimal.ZERO);
        user.addWallet(wallet);
        userRepository.save(user);
        log.info(messageConfig.getMessage(INFO_USER_CREATED, user.getUsername()));
        return CommandResponse.builder().id(user.getId()).build();
    }

    private AuthenticatedSession authenticatedSession(
            Authentication authentication,
            RefreshSessionService.RefreshToken refreshToken
    ) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return new AuthenticatedSession(
                toResponse(userDetails),
                jwtUtils.issueAccessToken(authentication),
                refreshToken
        );
    }

    private static AuthSessionResponse toResponse(UserDetailsImpl userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .sorted()
                .toList();
        return new AuthSessionResponse(
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getFirstName(),
                userDetails.getLastName(),
                roles
        );
    }

    private static BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Authentication failed");
    }

    public record AuthenticatedSession(
            AuthSessionResponse user,
            JwtUtils.AccessToken accessToken,
            RefreshSessionService.RefreshToken refreshToken
    ) {

        public AuthenticatedSession {
            Objects.requireNonNull(user, "user is required");
            Objects.requireNonNull(accessToken, "accessToken is required");
            Objects.requireNonNull(refreshToken, "refreshToken is required");
        }

        @Override
        public String toString() {
            return "AuthenticatedSession[user=" + user.username() + ", accessToken=<redacted>, refreshToken=<redacted>]";
        }
    }
}
