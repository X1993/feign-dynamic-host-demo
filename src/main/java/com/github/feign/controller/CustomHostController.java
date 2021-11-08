package com.github.feign.controller;

import com.github.feign.feign.CustomHostFeign;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.github.feign.env.DynamicHostClient.SERVICE_HOST_CONTEXT;

/**
 * @Author: X1993
 * @Date: 2021/3/30
 */
@RestController
public class CustomHostController {

    @Autowired
    private CustomHostFeign customHostFeign;

    @Value("${server.port:8080}")
    private int port;

    @GetMapping("/test1")
    public String test1(){
        return "test1";
    }

    @GetMapping("/test")
    public String test()
    {
        String host = "localhost:" + port;
        Assert.isTrue("test1".equals(customHostFeign.test1(host)));

        //通过线程变量设置请求host
        SERVICE_HOST_CONTEXT.set(host);
        Assert.isTrue("test1".equals(customHostFeign.test1()));

        SERVICE_HOST_CONTEXT.set("ssss");
        //测试高优先级
        customHostFeign.test1(host);

        SERVICE_HOST_CONTEXT.remove();

        return "OK";
    }

}
