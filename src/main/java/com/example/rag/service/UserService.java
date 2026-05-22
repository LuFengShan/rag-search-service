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

/**
 * 用户服务层
 * <p>
 * 负责用户的注册、登录、查询、更新和删除等核心业务逻辑。
 * 集成了密码加密、JWT令牌生成和刷新令牌管理功能。
 * </p>
 *
 * <h3>安全机制</h3>
 * <ul>
 *   <li>密码使用 BCrypt 算法加密存储</li>
 *   <li>登录时验证密码并生成 JWT 访问令牌</li>
 *   <li>刷新令牌存储在 Redis 中，支持安全的令牌刷新机制</li>
 * </ul>
 *
 * @see com.example.rag.security.JwtTokenProvider JWT令牌生成器
 * @see com.example.rag.service.RefreshTokenService 刷新令牌管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** 用户数据访问层 */
    private final UserMapper userMapper;

    /** 密码加密器（BCrypt） */
    private final PasswordEncoder passwordEncoder;

    /** JWT令牌生成器 */
    private final JwtTokenProvider jwtTokenProvider;

    /** 刷新令牌服务 */
    private final RefreshTokenService refreshTokenService;

    /**
     * 创建新用户（注册）
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>检查用户名是否已存在（唯一约束）</li>
     *   <li>检查邮箱是否已存在（唯一约束）</li>
     *   <li>使用 BCrypt 加密密码</li>
     *   <li>插入用户记录到数据库</li>
     * </ol>
     *
     * @param request 用户注册请求体，包含用户名、邮箱、密码、角色
     * @return 用户响应对象
     * @throws BusinessException 当用户名或邮箱已存在时抛出
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // 用户名唯一性校验
        if (userMapper.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        // 邮箱唯一性校验
        if (userMapper.existsByEmail(request.getEmail())) {
            throw new BusinessException("邮箱已存在");
        }

        // 构建用户实体，密码使用 BCrypt 加密
        User user = User.builder()
                .id(UUID.randomUUID())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() != null ? request.getRole() : User.Role.USER)
                .build();

        // 插入数据库
        userMapper.insert(user);
        log.info("User created successfully: {}", user.getUsername());
        return UserResponse.fromEntity(user);
    }

    /**
     * 用户登录
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>根据用户名查询用户</li>
     *   <li>验证密码是否匹配（BCrypt 验证）</li>
     *   <li>生成 JWT 访问令牌（有效期30分钟）</li>
     *   <li>生成刷新令牌（有效期7天）</li>
     *   <li>将刷新令牌存入 Redis</li>
     * </ol>
     *
     * @param request 登录请求体，包含用户名和密码
     * @return 登录响应对象，包含令牌信息和用户基本信息
     * @throws BadCredentialsException 当用户名或密码错误时抛出
     */
    public LoginResponse login(LoginRequest request) {
        // 根据用户名查询用户
        User user = userMapper.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));

        // 验证密码是否匹配
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        // 生成访问令牌和刷新令牌
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId().toString(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());

        // 将刷新令牌存入 Redis
        refreshTokenService.saveRefreshToken(user.getId().toString(), refreshToken);
        log.info("User logged in successfully: {}", user.getUsername());

        // 构建响应
        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    /**
     * 分页查询所有用户
     * <p>
     * 按创建时间降序排列，用于管理员查看用户列表。
     *
     * @param page 页码（从0开始）
     * @param pageSize 每页大小
     * @return 分页用户列表响应
     */
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

    /**
     * 根据ID查询用户
     *
     * @param id 用户UUID
     * @return 用户响应对象
     * @throws ResourceNotFoundException 当用户不存在时抛出
     */
    public UserResponse getUserById(UUID id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResourceNotFoundException("用户", "id", id.toString());
        }
        return UserResponse.fromEntity(user);
    }

    /**
     * 更新用户信息
     * <p>
     * 支持更新：用户名、邮箱、密码、角色
     * 更新用户名和邮箱时会进行唯一性校验。
     *
     * @param id 用户UUID
     * @param request 更新请求体
     * @return 更新后的用户响应对象
     * @throws ResourceNotFoundException 当用户不存在时抛出
     * @throws BusinessException 当用户名或邮箱已被其他用户使用时抛出
     */
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResourceNotFoundException("用户", "id", id.toString());
        }

        // 更新用户名（需检查唯一性）
        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userMapper.existsByUsername(request.getUsername())) {
                throw new BusinessException("用户名已存在");
            }
            user.setUsername(request.getUsername());
        }

        // 更新邮箱（需检查唯一性）
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userMapper.existsByEmail(request.getEmail())) {
                throw new BusinessException("邮箱已存在");
            }
            user.setEmail(request.getEmail());
        }

        // 更新密码（需重新加密）
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // 更新角色
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        // 保存更新
        userMapper.updateById(user);
        log.info("User updated successfully: {}", user.getUsername());
        return UserResponse.fromEntity(user);
    }

    /**
     * 删除用户
     *
     * @param id 用户UUID
     * @throws ResourceNotFoundException 当用户不存在时抛出
     */
    @Transactional
    public void deleteUser(UUID id) {
        if (userMapper.selectById(id) == null) {
            throw new ResourceNotFoundException("用户", "id", id.toString());
        }
        userMapper.deleteById(id);
        log.info("User deleted successfully: {}", id);
    }

    /**
     * 根据ID获取用户实体（内部使用）
     * <p>
     * 不同于 {@link #getUserById(UUID)}，此方法返回实体对象而非响应DTO。
     *
     * @param id 用户UUID
     * @return 用户实体对象
     * @throws ResourceNotFoundException 当用户不存在时抛出
     */
    public User getUserEntityById(UUID id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResourceNotFoundException("用户", "id", id.toString());
        }
        return user;
    }
}