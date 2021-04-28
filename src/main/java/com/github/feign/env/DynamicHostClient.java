package com.github.feign.env;

import feign.Client;
import feign.Request;
import feign.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 动态host client
 *
 * <p>
 *   //案例，注意，不要将这个类注入到spring容器，否则会导致ribbon失效
 *   @FeignClient(name = "random" ,configuration = DynamicHostClient.class)
 *   public interface CustomHostFeign {
 *
 *       @GetMapping("test")
 *       void test(@RequestHeader(HOST_HEADER) String host);
 *
 *   }
 *
 *   customHostFeign.test("10.100.100.16:8080") // curl get http://10.100.100.16:8080/test
 * </p>
 *
 * 有考虑使用ThreadLocal，但是不能支持hystrix
 *
 * @Author: X1993
 * @Date: 2021/3/25
 */
public class DynamicHostClient implements Client, ApplicationContextAware {

    private final static Logger LOGGER = LoggerFactory.getLogger(DynamicHostClient.class);

    public static final String HOST_HEADER = "CUSTOM_HOST";

    private Client delegate;

    @Override
    public Response execute(Request request, Request.Options options) throws IOException
    {
        String host = (String) request.headers().getOrDefault(HOST_HEADER ,Collections.EMPTY_LIST)
            .stream()
            .findFirst()
            .orElse(null);

        if (host == null){
            LOGGER.warn("request don't contain header [{0}] ,don't rewrite host ,direct execute" ,HOST_HEADER);
            return delegate.execute(request ,options);
        }

        URI uri = URI.create(request.url());
        String hostSign = uri.getHost();
        String newUrl = request.url().replaceFirst(hostSign ,host);
        Map<String, Collection<String>> newHeaders = request.headers()
                .entrySet()
                .stream()
                .filter(entry -> !HOST_HEADER.equals(entry.getKey()))
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));

        Request newRequest = Request.create(request.method(), newUrl,
                Collections.unmodifiableMap(newHeaders), request.body(), request.charset());

        LOGGER.debug("rewrite request {}" ,newRequest);

        return delegate.execute(newRequest ,options);
    }

    public interface Delegate extends Client{

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
    {
        delegate = applicationContext.getBeansOfType(Delegate.class)
                .values()
                .stream()
                .findFirst()
                .orElse(null);

        if (delegate == null) {
            BeanFactory parentBeanFactory = applicationContext.getParentBeanFactory();

            if (parentBeanFactory instanceof ListableBeanFactory) {
                this.delegate = ((ListableBeanFactory) parentBeanFactory).getBeansOfType(Client.class)
                        .values()
                        .stream()
                        .findFirst()
                        .orElse(null);

                if (this.delegate instanceof LoadBalancerFeignClient) {
                    this.delegate = ((LoadBalancerFeignClient) this.delegate).getDelegate();
                }
            }
        }

        if (this.delegate == null){
            this.delegate =  new Client.Default(null, null);
        }
    }

}
