package com.clf.mianshiren.utils;

import cn.dev33.satoken.stp.StpUtil;
import com.clf.mianshiren.common.ErrorCode;
import com.clf.mianshiren.email.SendEmailManager;
import com.clf.mianshiren.exception.BusinessException;
import com.clf.mianshiren.model.entity.User;
import com.clf.mianshiren.service.UserService;
import com.clf.mianshiren.service.impl.UserServiceImpl;
import org.apache.commons.mail.EmailException;
import org.redisson.api.*;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

/**
 *
 * 用来执行令牌桶相关操作
 *
 * @author clf
 * @version 1.0
 */
@Component
public class RedissonManager {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private UserService userService;

    @Resource
    private SendEmailManager sendEmailManager;

    //TODO 封禁时间由 Nacos 配置来完成

    private long interval = 1;

    private long count = 15;

    public void tryToInvokeController(String key, User user, HttpServletRequest request) {

        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

        RateIntervalUnit rateIntervalUnit = RateIntervalUnit.MINUTES;

        if(!rateLimiter.isExists()) {
            // 创建限流器，采用令牌桶的算法，算法是 每 interval 的时间单位 创建 count 个令牌。
            rateLimiter.trySetRate(RateType.OVERALL, count, interval, rateIntervalUnit);

            RBucket<Object> bucket = redissonClient.getBucket(key + ":status");
            bucket.set("active", 60, TimeUnit.MINUTES);
        }

        //尝试去获取令牌
        boolean flag = rateLimiter.tryAcquire(1);

        if(!flag) {
            if(user != null) {
                // 做封禁相关操作
                banUserHandler(user, request);
            } else {
                throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "请求次数过于频繁,请稍后再试");
            }
        }
    }

    private void banUserHandler(User user, HttpServletRequest request) {
        // 获取失败，说明令牌已经没有了，这个时候可以警告管理员，比如说发送邮箱
        // 封禁用户
        User updateuser = new User();
        updateuser.setUserRole("ban");
        updateuser.setId(user.getId());
        userService.updateById(updateuser);

        // 登出封禁用户的 token
        StpUtil.kickout(user.getId());

        try {
            sendEmailManager.sendBanMsgToAdminTask(user, request);
        } catch (EmailException e) {
            throw new RuntimeException("邮箱发送失败");
        }

        throw new BusinessException(ErrorCode.OPERATION_ERROR, "访问次数过滤频繁，已做封号处理");
    }



}
