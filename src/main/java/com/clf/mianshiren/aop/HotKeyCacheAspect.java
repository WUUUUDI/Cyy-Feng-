package com.clf.mianshiren.aop;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.clf.mianshiren.annotation.HotKeyCache;
import com.clf.mianshiren.common.BaseResponse;
import com.clf.mianshiren.common.ResultUtils;
import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author clf
 * @version 1.0
 */
@Order(2)
@Aspect
@Component
public class HotKeyCacheAspect {

    @Around("@annotation(hotKeyCache)")
    public Object handleHotKeyCache(ProceedingJoinPoint joinPoint, HotKeyCache hotKeyCache) throws Throwable {

        // 获取注解中的配置
        String keyPrefix = hotKeyCache.keyPrefix();
        Class<?> clz = hotKeyCache.type();

        // 获取参数
        Object[] args = joinPoint.getArgs();
        String keyExpression = args[0].toString();

        String key = generateKey(keyPrefix, keyExpression);

        // 判断是否为热 key
        if(JdHotKeyStore.isHotKey(key)) {
            Object obj = JdHotKeyStore.get(key);
            if(obj != null) {
                return ResultUtils.success(clz.cast(obj));
            }
        }

        // 执行原方法
        Object result = joinPoint.proceed();

        // 缓存热点 key
        if(result != null) {

            BaseResponse baseResponse = BeanUtil.toBean(result, BaseResponse.class);
            Object data = baseResponse.getData();

            JdHotKeyStore.smartSet(key, data);
        }

        return result;
    }

    public String generateKey(String keyPrefix, String keyExpression) {

        if(StrUtil.isNotBlank(keyPrefix)) {
            return keyPrefix + keyExpression;
        }

        return keyExpression;
    }

}
