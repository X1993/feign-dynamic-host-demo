package com.github.feign.feign;

import com.github.feign.env.DynamicHostClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import static com.github.feign.env.DynamicHostClient.HOST_HEADER;

/**
 * @Author: junjie
 * @Date: 2021/3/30
 */
@FeignClient(name = "random" ,configuration = DynamicHostClient.class)
public interface CustomHostFeign {

    /**
     * 通过名为{@link DynamicHostClient#HOST_HEADER}的头
     * @param host
     */
    @GetMapping("test")
    void test(@RequestHeader(HOST_HEADER) String host);

}