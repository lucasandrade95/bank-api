package com.lucasandrade.bankapi.auth;

import com.lucasandrade.bankapi.auth.dto.AuthResponse;
import com.lucasandrade.bankapi.auth.dto.LoginRequest;
import com.lucasandrade.bankapi.auth.dto.RegisterRequest;
import com.lucasandrade.bankapi.shared.BusinessException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AppUserRepository repository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /** Cadastra um novo usuario (senha guardada como hash) e ja devolve um token. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (repository.existsByUsername(request.username())) {
            throw new BusinessException("Username ja esta em uso");
        }
        AppUser user = new AppUser(request.username(), passwordEncoder.encode(request.password()));
        repository.save(user);
        return tokenFor(user.getUsername());
    }

    /** Valida as credenciais e troca por um token JWT. Credencial invalida -> 401. */
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        return tokenFor(request.username());
    }

    private AuthResponse tokenFor(String username) {
        String token = jwtService.generateToken(username);
        return AuthResponse.bearer(token, jwtService.getExpirationSeconds());
    }
}
