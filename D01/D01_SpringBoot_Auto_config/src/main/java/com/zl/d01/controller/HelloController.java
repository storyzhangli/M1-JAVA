package com.zl.d01.controller;

import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    public String sayHello(){
        return "Hello World";
    }
}
