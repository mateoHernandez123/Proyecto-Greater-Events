package ar.edu.unnoba.pdyc2026.usersocial.config;

import ar.edu.unnoba.pdyc2026.common.exception.BusinessRuleException;
import ar.edu.unnoba.pdyc2026.common.exception.ResourceNotFoundException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    public ErrorDecoder catalogErrorDecoder() {
        return new CatalogErrorDecoder();
    }

    private static final class CatalogErrorDecoder implements ErrorDecoder {

        private final ErrorDecoder defaultDecoder = new Default();

        @Override
        public Exception decode(String methodKey, Response response) {
            if (response.status() == 404) {
                return new ResourceNotFoundException("Catalog resource not found");
            }
            if (response.status() >= 400 && response.status() < 500) {
                return new BusinessRuleException("Catalog request rejected: HTTP " + response.status());
            }
            return defaultDecoder.decode(methodKey, response);
        }
    }
}
