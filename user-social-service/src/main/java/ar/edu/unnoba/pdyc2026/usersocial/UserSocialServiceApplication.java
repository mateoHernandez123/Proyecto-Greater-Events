package ar.edu.unnoba.pdyc2026.usersocial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"ar.edu.unnoba.pdyc2026.usersocial", "ar.edu.unnoba.pdyc2026.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class UserSocialServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserSocialServiceApplication.class, args);
    }
}
