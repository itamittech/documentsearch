package com.enterprise.docsearch.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = {
    "com.enterprise.docsearch.search",
    "com.enterprise.docsearch.common"
})
@EnableCaching
public class SearchServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
