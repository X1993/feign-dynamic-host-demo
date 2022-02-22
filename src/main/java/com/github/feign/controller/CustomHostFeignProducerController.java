package com.github.feign.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供本地Feign调用
 * @see com.github.feign.feign.CustomHostFeign
 * @Author: X1993
 * @Date: 2021/3/30
 */
@RestController
public class CustomHostFeignProducerController {

    @GetMapping("/test1")
    public String test1(){
        return "test1";
    }

}
