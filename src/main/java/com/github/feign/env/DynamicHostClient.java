package com.github.feign.env;

import com.netflix.hystrix.HystrixInvokable;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableDefault;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
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
 *   //案例，注意，不要将{@link DynamicHostClient}注入到spring容器，否则会导致ribbon失效
 *
 *   @FeignClient(name = "custom-name" ,configuration = DynamicHostClient.class)
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
 * @Author: X1993
 * @Date: 2021/3/25
 */
public class DynamicHostClient implements Client, ApplicationContextAware {

    private final static Logger LOGGER = LoggerFactory.getLogger(DynamicHostClient.class);

    /**
     * 通过请求头定义访问服务地址,优先级大于{@link DynamicHostClient#SERVICE_HOST_CONTEXT}
     *
     * <p>
     *     @FeignClient(name = "custom-name" ,configuration = DynamicHostClient.class)
     *     public interface CustomHostFeign {
     *
     *       @GetMapping("test")
     *       void test(@RequestHeader(HOST_HEADER) String host);
     *
     *     }
     * </p>
     */
    public static final String HOST_HEADER = "CUSTOM_HOST";

    /**
     * 通过线程变量定义访问服务地址
     */
    public static final ThreadLocal<String> SERVICE_HOST_CONTEXT = new ThreadLocal<>();

    private Client delegate;

    static {
        HystrixPlugins.getInstance().registerCommandExecutionHook(new HystrixThreadLocalHook());
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException
    {
        String host = (String) request.headers().getOrDefault(HOST_HEADER ,Collections.EMPTY_LIST)
            .stream()
            .findFirst()
            .orElse(SERVICE_HOST_CONTEXT.get());

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

    /**
     * @see <a href="https://segmentfault.com/a/1190000037781121?utm_source=tag-newest">Hystrix 如何解决 ThreadLocal 信息丢失</a>
     */
    private static class HystrixThreadLocalHook extends HystrixCommandExecutionHook {

        private HystrixRequestVariableDefault<String> requestVariable = new HystrixRequestVariableDefault<>();

        private HystrixThreadLocalHook() {
        }

        @Override
        public <T> void onStart(HystrixInvokable<T> commandInstance) {
            HystrixRequestContext.initializeContext();
            copyTraceId();
        }

        @Override
        public <T> void onExecutionStart(HystrixInvokable<T> commandInstance) {
            pasteTraceId();
        }

        @Override
        public <T> void onFallbackStart(HystrixInvokable<T> commandInstance) {
            pasteTraceId();
        }

        @Override
        public <T> void onSuccess(HystrixInvokable<T> commandInstance) {
            HystrixRequestContext.getContextForCurrentThread().shutdown();
            super.onSuccess(commandInstance);
        }

        @Override
        public <T> Exception onError(HystrixInvokable<T> commandInstance, HystrixRuntimeException.FailureType failureType, Exception e) {
            HystrixRequestContext.getContextForCurrentThread().shutdown();
            return super.onError(commandInstance, failureType, e);
        }

        private void copyTraceId() {
            requestVariable.set(SERVICE_HOST_CONTEXT.get());
        }

        private void pasteTraceId() {
            SERVICE_HOST_CONTEXT.set(requestVariable.get());
        }

    }


}
