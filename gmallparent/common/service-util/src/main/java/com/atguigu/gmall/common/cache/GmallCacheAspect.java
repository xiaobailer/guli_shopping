package com.atguigu.gmall.common.cache;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.RedisConst;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * @author mqx
 * 处理环绕通知
 * @date 2020-11-11 09:30:29
 */
@Component
@Aspect
public class GmallCacheAspect {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    //  切GmallCache注解
    @SneakyThrows
    @Around("@annotation(com.atguigu.gmall.common.cache.GmallCache)")
    public Object cacheAroundAdvice(ProceedingJoinPoint joinPoint){
        //  声明一个对象
        Object object = new Object();
        //  在环绕通知中处理业务逻辑 {实现分布式锁}
        //  获取到注解，注解使用在方法上！
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        GmallCache gmallCache = signature.getMethod().getAnnotation(GmallCache.class);
        //  获取到注解上的前缀
        String prefix = gmallCache.prefix(); // sku
        //  方法传入的参数
        Object[] args = joinPoint.getArgs();
        //  组成缓存的key 需要前缀+方法传入的参数
        String key = prefix+ Arrays.asList(args).toString();
        //  防止redis ，redisson 出现问题！
        try {
            //  从缓存中获取数据
            //  类似于skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);
            object = cacheHit(key,signature);
            //  判断缓存中的数据是否为空！
            if (object==null){
                //  从数据库中获取数据，并放入缓存，防止缓存击穿必须上锁
                //  perfix = sku  index1 skuId = 32 , index2 skuId = 33
                //  public SkuInfo getSkuInfo(Long skuId)
                //  key+":lock"
                String lockKey = prefix + ":lock";
                //  准备上锁
                RLock lock = redissonClient.getLock(lockKey);
                boolean result = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);
                //  上锁成功
                if (result){
                    try {
                        //  表示执行方法体 getSkuInfoDB(skuId);
                        object = joinPoint.proceed(joinPoint.getArgs());
                        //  判断object 是否为空
                        if (object==null){
                            //  防止缓存穿透
                            Object object1 = new Object();
                            redisTemplate.opsForValue().set(key, JSON.toJSONString(object1),RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                            //  返回数据
                            return object1;
                        }
                        //  放入缓存
                        redisTemplate.opsForValue().set(key, JSON.toJSONString(object),RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);

                        //  返回数据
                        return object;
                    } finally {
                        lock.unlock();
                    }
                }else{
                    //  上锁失败,睡眠自旋
                    Thread.sleep(1000);
                    return cacheAroundAdvice(joinPoint);
                    //  理想状态
                    //  return cacheHit(key, signature);
                }
            }else {
                return object;
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        //  如果出现问题数据库兜底
        return joinPoint.proceed(joinPoint.getArgs());
    }
    /**
     *  表示从缓存中获取数据
     * @param key 缓存的key
     * @param signature 获取方法的返回值类型
     * @return
     */
    private Object cacheHit(String key, MethodSignature signature) {
        //  通过key 来获取缓存的数据
        String strJson = (String) redisTemplate.opsForValue().get(key);
        //  表示从缓存中获取到了数据
        if (!StringUtils.isEmpty(strJson)){
            //  字符串存储的数据是什么?   就是方法的返回值类型
            Class returnType = signature.getReturnType();
            //  将字符串变为当前的返回值类型
            return JSON.parseObject(strJson,returnType);
        }
        return null;
    }
}
