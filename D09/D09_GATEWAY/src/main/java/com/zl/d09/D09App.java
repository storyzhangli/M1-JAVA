package com.zl.d09;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class D09App {

    @RequestMapping
    public String home() {
        return "Hello World | hello D09";
    }
    public static void main(String[] args) {
        SpringApplication.run(D09App.class, args);
    }
}
