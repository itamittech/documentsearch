package com.enterprise.docsearch.index;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "com.enterprise.docsearch.index",
    "com.enterprise.docsearch.common"
})
public class IndexServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(IndexServiceApplication.class, args);
    }
}
