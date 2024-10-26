package com.clf.mianshiren.sentinel;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;

/**
 * 用来统一管理 Sentinel Rules
 *
 * @author clf
 * @version 1.0
 */
@Component
public class SentinelRulesManager {

    @PostConstruct
    public void init() {
        initFlowRules();
        initDegradeRules();
    }

    /**
     * 用来定义 限流规则
     */
    public void initFlowRules() {

        // 单个用户 ip 的 listQuestionVOByPage 资源的限流
        ParamFlowRule flowRule1 = new ParamFlowRule(SentinelResourceNameConstant.LIST_QUESTIONVO_BY_PAGE)
                .setParamIdx(0) // 对第一个参数热点限流，即我们传入的第一个参数 ip
                .setCount(30) // 设置周期内的次数阈值
                .setDurationInSec(60); // 设置一个周期的时长

        // 单个用户 ip 的 listQuestionBankVOByPage 资源的限流
        ParamFlowRule flowRule2 = new ParamFlowRule(SentinelResourceNameConstant.LIST_QUESTIONBANKVO_BY_PAGE)
                .setParamIdx(0) // 对第一个参数热点限流，即我们传入的第一个参数 ip
                .setCount(30) // 设置周期内的次数阈值
                .setDurationInSec(60); // 设置一个周期的时长

        // 单个用户 ip 的 listQuestionBankQuestionVOByPage 资源的限流
        ParamFlowRule flowRule3 = new ParamFlowRule(SentinelResourceNameConstant.LIST_QUESTIONBANKQUESTIONVO_BY_PAGE)
                .setParamIdx(0) // 对第一个参数热点限流，即我们传入的第一个参数 ip
                .setCount(30) // 设置周期内的次数阈值
                .setDurationInSec(60); // 设置一个周期的时长

        ParamFlowRuleManager.loadRules(Arrays.asList(flowRule1, flowRule2, flowRule3)); // 加载我们的限流策略到 限流规则管理器
    }

    /**
     * 用来定义 熔断规则
     */
    public void initDegradeRules() {

        // 给 listQuestionVOByPage 配置熔断规则
        // 慢调用异常规则
        DegradeRule slowCallRule = new DegradeRule(SentinelResourceNameConstant.LIST_QUESTIONVO_BY_PAGE)
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(0.2) // 设置慢调用比例
                .setTimeWindow(60) // 设置熔断时间
                .setStatIntervalMs(30 * 1000) // 设置统计时长 30秒
                .setMinRequestAmount(10) // 最小请求数 10
                .setSlowRatioThreshold(3); // 响应时间超过 3 秒

        // 异常比率规则
        DegradeRule errorRateRule = new DegradeRule(SentinelResourceNameConstant.LIST_QUESTIONVO_BY_PAGE)
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.1)
                .setTimeWindow(60)
                .setStatIntervalMs(30 * 1000)
                .setMinRequestAmount(10);


        // 给 listQuestionBankVOByPage 配置熔断规则
        // 慢调用异常规则
        DegradeRule slowCallRule2 = new DegradeRule(SentinelResourceNameConstant.LIST_QUESTIONBANKVO_BY_PAGE)
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(0.2) // 设置慢调用比例
                .setTimeWindow(60) // 设置熔断时间
                .setStatIntervalMs(30 * 1000) // 设置统计时长 30秒
                .setMinRequestAmount(10) // 最小请求数 10
                .setSlowRatioThreshold(3); // 响应时间超过 3 秒

        // 异常比率规则
        DegradeRule errorRateRule2 = new DegradeRule(SentinelResourceNameConstant.LIST_QUESTIONBANKVO_BY_PAGE)
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.1)
                .setTimeWindow(60)
                .setStatIntervalMs(30 * 1000)
                .setMinRequestAmount(10);


        // 给 listQuestionBankQuestionVOByPage 配置熔断规则
        // 慢调用异常规则
        DegradeRule slowCallRule3 = new DegradeRule(SentinelResourceNameConstant.LIST_QUESTIONBANKQUESTIONVO_BY_PAGE)
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(0.2) // 设置慢调用比例
                .setTimeWindow(60) // 设置熔断时间
                .setStatIntervalMs(30 * 1000) // 设置统计时长 30秒
                .setMinRequestAmount(10) // 最小请求数 10
                .setSlowRatioThreshold(3); // 响应时间超过 3 秒

        // 异常比率规则
        DegradeRule errorRateRule3 = new DegradeRule(SentinelResourceNameConstant.LIST_QUESTIONBANKQUESTIONVO_BY_PAGE)
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.1)
                .setTimeWindow(60)
                .setStatIntervalMs(30 * 1000)
                .setMinRequestAmount(10);

        // 加载熔断规则
        DegradeRuleManager.loadRules(Arrays.asList(slowCallRule, slowCallRule2, slowCallRule3, errorRateRule, errorRateRule2, errorRateRule3));
    }


}
