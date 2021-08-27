### 简介
通过阅读feign源码的过程中想到一种动态指定host的方式，简单写了个demo

>   在微服务架构中，有时调用链比较长，本地调试需要启动很多服务，可参考重写*feign.Client*实现远程连接开发环境依赖服务

### 实现原理
通过特定请求头/线程变量（ThreadLocal）指定host，自定义*feign.Client*从请求头/线程变量中取到host替换服务名（原host）生成新的请求
```java
    /**
    * 实现参考
    * @see com.github.feign.env.DynamicHostClient 
    */
```
