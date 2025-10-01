package com.enterprise.docsearch.document;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = {
    "com.enterprise.docsearch.document",
    "com.enterprise.docsearch.common"
})
@EnableJpaAuditing
@EnableCaching
public class DocumentServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }
}
