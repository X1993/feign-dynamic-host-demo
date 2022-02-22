package com.github.feign.feign;

import com.github.feign.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.Assert;
import static com.github.feign.env.DynamicHostClient.SERVICE_HOST_CONTEXT;

/**
 * @author wangjj7
 * @date 2022/2/22
 * @description
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class ,webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class CustomHostFeignTest {

    @Autowired
    private CustomHostFeign customHostFeign;

    @Value("${server.port:8080}")
    private int port;

    @Test
    public void test()
    {
        String host = "localhost:" + port;
        Assert.isTrue("test1".equals(customHostFeign.test1(host)));

        //通过线程变量设置请求host
        SERVICE_HOST_CONTEXT.set(host);
        Assert.isTrue("test1".equals(customHostFeign.test1()));

        SERVICE_HOST_CONTEXT.set("localhost:-1");
        //测试高优先级
        customHostFeign.test1(host);

        SERVICE_HOST_CONTEXT.remove();
    }

}