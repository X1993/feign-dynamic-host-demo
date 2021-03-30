### 简介
阅读feign源码的过程中想到一种动态指定host的方式，简单写了个demo

### 实现原理
通过特定请求头指定host，之后自定义*feign.Client*从请求头中取到host替换服务名（原host）生成新的请求
```java
    /**
    * 实现参考
    * @see com.github.feign.env.DynamicHostClient 
    */
```
