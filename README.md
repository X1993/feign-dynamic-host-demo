### 简介
通过阅读feign源码的过程中想到一种动态指定host的方式，简单写了个demo

### 实现原理
通过特定请求头/线程变量（ThreadLocal）指定host，自定义*feign.Client*从请求头/线程变量中取到host替换服务名（原host）生成新的请求
```java
    /**
    * @see com.github.feign.env.DynamicHostClient 实现
    * @see com.github.feign.feign.CustomHostFeignTest 使用参考
    */
```
