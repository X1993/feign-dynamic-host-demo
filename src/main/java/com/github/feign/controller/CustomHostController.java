package com.github.feign.controller;

import com.github.feign.feign.CustomHostFeign;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: X1993
 * @Date: 2021/3/30
 */
@RestController
public class CustomHostController {

    @Autowired
    private CustomHostFeign customHostFeign;

    @GetMapping("test")
    public void test(@RequestParam(value = "host") String host)
    {
        customHostFeign.test(host);
    }

}
