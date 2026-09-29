package com.github.yildizmy.dto.mapper;

import com.github.yildizmy.domain.entity.Role;
import com.github.yildizmy.domain.enums.RoleType;
import com.github.yildizmy.dto.request.SignupRequest;
import com.github.yildizmy.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupRequestMapperTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleService roleService;

    private SignupRequestMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SignupRequestMapperImpl();
        mapper.setPasswordEncoder(passwordEncoder);
        mapper.setRoleService(roleService);
    }

    @Test
    void publicSignupAlwaysCreatesANewRegularUser() {
        SignupRequest request = new SignupRequest(
                "test",
                "buyer",
                " Buyer ",
                " BUYER@EXAMPLE.COM ",
                "plaintext-password"
        );
        Role userRole = new Role();
        userRole.setId(1L);
        userRole.setType(RoleType.ROLE_USER);
        when(passwordEncoder.encode("plaintext-password")).thenReturn("encoded-password");
        when(roleService.getReferenceByTypeIsIn(Set.of(RoleType.ROLE_USER))).thenReturn(List.of(userRole));

        var user = mapper.toUser(request);

        assertThat(user.getId()).isNull();
        assertThat(user.getUsername()).isEqualTo("buyer");
        assertThat(user.getEmail()).isEqualTo("buyer@example.com");
        assertThat(user.getPassword()).isEqualTo("encoded-password");
        assertThat(user.getRoles()).containsExactly(userRole);
        verify(roleService).getReferenceByTypeIsIn(Set.of(RoleType.ROLE_USER));
    }
}
