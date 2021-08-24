package com.github.feign.controller;

import com.github.feign.feign.CustomHostFeign;
import org.springframework.beans.factory.annotation.Autowired;
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

    @GetMapping("/test")
    public void test()
    {
        String host = "www.baidu.com";
        //通过请求头设置访问地址
        try {
            //http://www.baidu.com/test
            customHostFeign.test(host);
        }catch (Exception e){

        }

        //通过线程变量设置请求host
        SERVICE_HOST_CONTEXT.set(host);
        try {
            //http://www.baidu.com/test
            customHostFeign.test();
        }catch (Exception e){

        }

        try {
            //测试高优先级
            //http://www.taobao.com/test
            customHostFeign.test("www.taobao.com");
        }catch (Exception e){

        }

        SERVICE_HOST_CONTEXT.remove();
    }

}
