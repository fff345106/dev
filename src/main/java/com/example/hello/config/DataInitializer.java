package com.example.hello.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserRepository;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // 检查是否已存在超级管理员
            boolean hasSuperAdmin = userRepository.findAll().stream()
                    .anyMatch(user -> user.getRole() == UserRole.SUPER_ADMIN);

            if (!hasSuperAdmin) {
                // 检查 admin 用户名是否已存在
                if (userRepository.existsByUsername("admin")) {
                    System.out.println("admin 用户已存在，跳过超级管理员初始化");
                    return;
                }
                // 创建默认超级管理员
                User superAdmin = new User(
                        "admin",
                        passwordEncoder.encode("admin123"),
                        UserRole.SUPER_ADMIN
                );
                userRepository.save(superAdmin);
                System.out.println("===========================================");
                System.out.println("默认超级管理员已创建:");
                System.out.println("用户名: admin");
                System.out.println("密码: admin123");
                System.out.println("请登录后及时修改密码！");
                System.out.println("===========================================");
            } else {
                System.out.println("超级管理员已存在，跳过初始化");
            }
        };
    }
}
