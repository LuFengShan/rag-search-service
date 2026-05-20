package com.example.rag.config;

import com.example.rag.entity.User;
import com.example.rag.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userMapper.selectCount(null) == 0) {
            User admin = User.builder()
                    .id(UUID.randomUUID())
                    .username("admin")
                    .email("admin@example.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role(User.Role.ADMIN)
                    .build();
            userMapper.insert(admin);
            log.info("初始化管理员用户: admin / admin123");

            User kbAdmin = User.builder()
                    .id(UUID.randomUUID())
                    .username("kbadmin")
                    .email("kbadmin@example.com")
                    .password(passwordEncoder.encode("kbadmin123"))
                    .role(User.Role.KNOWLEDGE_BASE_ADMIN)
                    .build();
            userMapper.insert(kbAdmin);
            log.info("初始化知识库管理员: kbadmin / kbadmin123");

            User user = User.builder()
                    .id(UUID.randomUUID())
                    .username("user")
                    .email("user@example.com")
                    .password(passwordEncoder.encode("user123"))
                    .role(User.Role.USER)
                    .build();
            userMapper.insert(user);
            log.info("初始化普通用户: user / user123");
        }
    }
}