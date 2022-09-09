package com.atguigu.gmall.common.cache;

import java.lang.annotation.*;

/**
 * @author mqx
 * @date 2020-11-11 09:21:16
 */
@Target(ElementType.METHOD) //  注解使用范围
@Retention(RetentionPolicy.RUNTIME) //  注解的生命周期
public @interface GmallCache {

    //  定义一个组成缓存中 key 的前缀！
    String prefix() default "cache";
}
