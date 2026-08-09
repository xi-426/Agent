package com.yan.agent.auth;

import com.yan.agent.auth.dto.AuthResponse;
import com.yan.agent.auth.dto.LoginRequest;
import com.yan.agent.auth.dto.RegisterRequest;
import com.yan.agent.user.AppUser;
import com.yan.agent.user.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email()
                .trim()
                .toLowerCase();

        // 相同邮箱不能重复注册
        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException(
                    "该邮箱已经注册");
        }

        // 明文密码经过 BCrypt 处理后才能存入数据库
        String passwordHash = passwordEncoder.encode(
                request.password());

        // 此时对象只在内存中，还没有数据库生成的 id
        AppUser user = new AppUser(
                email,
                request.displayName().trim(),
                passwordHash);

        // INSERT 到 app_user，保存后才有 id
        AppUser savedUser = userRepository.save(user);

        // 使用包含 id 的用户生成 JWT
        return toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email()
                .trim()
                .toLowerCase();

        // 根据邮箱查询数据库
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "邮箱或密码错误"));

        // 用明文密码和数据库中的 BCrypt 哈希进行验证
        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user.getPasswordHash());

        if (!passwordMatches) {
            throw new InvalidCredentialsException(
                    "邮箱或密码错误");
        }

        // 登录成功，生成并返回新的 JWT
        return toResponse(user);
    }

    private AuthResponse toResponse(AppUser user) {
        String token = jwtService.createToken(user);
        return new AuthResponse(token, "Bearer",
                jwtService.getExpiresInSeconds(), user.getId(),
                user.getEmail(), user.getDisplayName());
    }

}
