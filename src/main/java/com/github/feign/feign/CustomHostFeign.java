package com.github.feign.feign;

import com.github.feign.env.DynamicHostClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import static com.github.feign.env.DynamicHostClient.HOST_HEADER;

/**
 * @Author: X1993
 * @Date: 2021/3/30
 */
@FeignClient(name = "custom-service", configuration = {DynamicHostClient.class})
public interface CustomHostFeign {

    /**
     * 通过名为{@link DynamicHostClient#HOST_HEADER}的请求头设置请求host
     * @param host 指定请求host
     */
    @GetMapping("/test")
    void test(@RequestHeader(HOST_HEADER) String host);

    /**
     * 通过{@link DynamicHostClient#SERVICE_HOST_CONTEXT}设置请求host
     */
    @GetMapping("/test")
    void test();

}
