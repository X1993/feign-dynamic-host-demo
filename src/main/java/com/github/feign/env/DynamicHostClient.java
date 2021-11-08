package com.github.feign.env;

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.*;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import feign.Client;
import feign.Request;
import feign.Response;
import org.apache.commons.lang.StringUtils;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 动态host client
 *
 * <p>
 *   //案例
 *
 *   // 如果将{@link DynamicHostClient}注入到spring容器，会覆盖默认Client，导致ribbon失效，通过configuration属性引用即可
 *   @FeignClient(name = "custom-name" ,configuration = DynamicHostClient.class)
 *   public interface CustomHostFeign {
 *
 *       //通过名为{@link DynamicHostClient#HOST_HEADER}的请求头设置请求host，优先级高
 *       @GetMapping("test")
 *       void test(@RequestHeader(HOST_HEADER) String host);
 *
 *       //通过{@link DynamicHostClient#SERVICE_HOST_CONTEXT}设置请求host
 *       @GetMapping("/test")
 *       void test();
 *
 *   }
 *
 *   String host = "10.100.100.16:8080"
 *   customHostFeign.test(host) // curl get http://10.100.100.16:8080/test
 *
 *   SERVICE_HOST_CONTEXT.set(host);
 *   customHostFeign.test(); // curl get http://10.100.100.16:8080/test
 *
 * </p>
 *
 * @Author: X1993
 * @Date: 2021/3/25
 */
public class DynamicHostClient implements Client, ApplicationContextAware {

    private final static Logger LOGGER = LoggerFactory.getLogger(DynamicHostClient.class);

    /**
     * 通过请求头定义访问服务地址,优先级大于{@link DynamicHostClient#SERVICE_HOST_CONTEXT}
     */
    public static final String HOST_HEADER = "CUSTOM_HOST";

    /**
     * 通过线程变量定义访问服务地址
     */
    public static final ThreadLocal<String> SERVICE_HOST_CONTEXT = new ThreadLocal<>();

    private Client delegate;

    static {
        HystrixPlugins instance = HystrixPlugins.getInstance();
        HystrixMetricsPublisher metricsPublisher = instance.getMetricsPublisher();
        HystrixEventNotifier eventNotifier = instance.getEventNotifier();
        HystrixPropertiesStrategy propertiesStrategy = instance.getPropertiesStrategy();
        HystrixCommandExecutionHook commandExecutionHook = instance.getCommandExecutionHook();
        HystrixConcurrencyStrategy concurrencyStrategy = instance.getConcurrencyStrategy();
        HystrixPlugins.reset();
        instance.registerMetricsPublisher(metricsPublisher);
        instance.registerEventNotifier(eventNotifier);
        instance.registerPropertiesStrategy(propertiesStrategy);
        instance.registerCommandExecutionHook(commandExecutionHook);
        instance.registerConcurrencyStrategy(new ThreadLocalCopyHystrixConcurrencyStrategy(concurrencyStrategy));

    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException
    {
        String host = (String) request.headers().getOrDefault(HOST_HEADER ,Collections.EMPTY_LIST)
            .stream()
            .findFirst()  //如果请求头为空，默认从线程变量获取
            .orElse(SERVICE_HOST_CONTEXT.get());

        if (StringUtils.isBlank(host)){
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
     * <href="https://blog.csdn.net/songhaifengshuaige/article/details/80345012">Hystrix实现ThreadLocal上下文的传递</href>
     */
    private static class ThreadLocalCopyHystrixConcurrencyStrategy extends HystrixConcurrencyStrategy {

        /**
         * {@link HystrixPlugins#registerConcurrencyStrategy(HystrixConcurrencyStrategy)}不支持添加多个策略，
         * 这里利用装饰者模式避免覆盖已注册的策略
         */
        private final HystrixConcurrencyStrategy target;

        private ThreadLocalCopyHystrixConcurrencyStrategy(HystrixConcurrencyStrategy target) {
            this.target = target;
        }

        @Override
        public ThreadPoolExecutor getThreadPool(HystrixThreadPoolKey threadPoolKey,
                                                HystrixProperty<Integer> corePoolSize,
                                                HystrixProperty<Integer> maximumPoolSize,
                                                HystrixProperty<Integer> keepAliveTime,
                                                TimeUnit unit,
                                                BlockingQueue<Runnable> workQueue)
        {
            if (target == null) {
                return super.getThreadPool(threadPoolKey, corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
            }
            return target.getThreadPool(threadPoolKey, corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }

        @Override
        public BlockingQueue<Runnable> getBlockingQueue(int maxQueueSize) {
            if (target == null) {
                return super.getBlockingQueue(maxQueueSize);
            }
            return target.getBlockingQueue(maxQueueSize);
        }

        @Override
        public <T> HystrixRequestVariable<T> getRequestVariable(HystrixRequestVariableLifecycle<T> rv) {
            if (target == null) {
                return super.getRequestVariable(rv);
            }
            return target.getRequestVariable(rv);
        }

        @Override
        public <T> Callable<T> wrapCallable(Callable<T> callable)
        {
            if (target == null){
                return super.wrapCallable(callable);
            }
            return target.wrapCallable(new ThreadLocalCopyCallable<>(callable));
        }

        class ThreadLocalCopyCallable<T> implements Callable<T>
        {
            private final String host;

            private final Callable<T> callable;

            public ThreadLocalCopyCallable(Callable<T> callable) {
                //这一步在主线程中执行
                host = SERVICE_HOST_CONTEXT.get();
                this.callable = callable;
            }

            @Override
            public T call() throws Exception
            {
                //这一步在hystrix线程池中执行
                try {
                    SERVICE_HOST_CONTEXT.set(host);
                    return callable.call();
                }finally {
                    SERVICE_HOST_CONTEXT.remove();
                }
            }
        }
    }


}
