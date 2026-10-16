package com.zl.d03;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class D03App {

    @RequestMapping
    public String home() {
        return "Hello World | hello D01";
    }
    public static void main(String[] args) {
        SpringApplication.run(D03App.class, args);
    }
}
