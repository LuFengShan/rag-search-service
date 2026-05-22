package com.example.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.dto.request.CreateUserRequest;
import com.example.rag.dto.request.LoginRequest;
import com.example.rag.dto.request.UpdateUserRequest;
import com.example.rag.dto.response.LoginResponse;
import com.example.rag.dto.response.PagedResponse;
import com.example.rag.dto.response.UserResponse;
import com.example.rag.entity.User;
import com.example.rag.exception.BusinessException;
import com.example.rag.exception.ResourceNotFoundException;
import com.example.rag.mapper.UserMapper;
import com.example.rag.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider jwtTokenProvider;

    private final RefreshTokenService refreshTokenService;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userMapper.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (userMapper.existsByEmail(request.getEmail())) {
            throw new BusinessException("邮箱已存在");
        }

        User user = User.builder()
                .id(UUID.randomUUID())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() != null ? request.getRole() : User.Role.USER)
                .build();

        userMapper.insert(user);
        return UserResponse.fromEntity(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId().toString(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());
        
        refreshTokenService.saveRefreshToken(user.getId().toString(), refreshToken);
        log.info("User logged in successfully: {}", user.getUsername());

        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    public PagedResponse<UserResponse> getAllUsers(int page, int pageSize) {
        Page<User> pageParam = new Page<>(page + 1, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getCreatedAt);
        IPage<User> userPage = userMapper.selectPage(pageParam, wrapper);

        return PagedResponse.<UserResponse>builder()
                .list(userPage.getRecords().stream().map(UserResponse::fromEntity).toList())
                .total(userPage.getTotal())
                .page(page)
                .pageSize(pageSize)
                .totalPages((int) userPage.getPages())
                .build();
    }

    public UserResponse getUserById(UUID id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResourceNotFoundException("用户", "id", id.toString());
        }
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResourceNotFoundException("用户", "id", id.toString());
        }

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userMapper.existsByUsername(request.getUsername())) {
                throw new BusinessException("用户名已存在");
            }
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userMapper.existsByEmail(request.getEmail())) {
                throw new BusinessException("邮箱已存在");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        userMapper.updateById(user);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        if (userMapper.selectById(id) == null) {
            throw new ResourceNotFoundException("用户", "id", id.toString());
        }
        userMapper.deleteById(id);
    }

    public User getUserEntityById(UUID id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResourceNotFoundException("用户", "id", id.toString());
        }
        return user;
    }
}