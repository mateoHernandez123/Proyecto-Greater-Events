package ar.edu.unnoba.pdyc2026.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"ar.edu.unnoba.pdyc2026.catalog", "ar.edu.unnoba.pdyc2026.common"})
@EnableDiscoveryClient
public class CatalogEventServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogEventServiceApplication.class, args);
    }
}
