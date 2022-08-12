package com.github.feign.feign.mock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供本地Feign调用
 * @see CustomHostFeign
 * @Author: X1993
 * @Date: 2021/3/30
 */
@RestController
public class CustomHostFeignControllerMock {

    public static final String MOCK_SERVER_RESULT = "OK";

    /**
     * 模拟{@link CustomHostFeign}调用的资源
     * @return
     */
    @GetMapping(CustomHostFeign.MOCK_SERVER_URN)
    public String mockServer(){
        return MOCK_SERVER_RESULT;
    }

}
