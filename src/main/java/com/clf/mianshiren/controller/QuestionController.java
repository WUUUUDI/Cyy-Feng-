package com.clf.mianshiren.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.json.JSONUtil;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clf.mianshiren.annotation.AuthCheck;
import com.clf.mianshiren.annotation.HotKeyCache;
import com.clf.mianshiren.common.BaseResponse;
import com.clf.mianshiren.common.DeleteRequest;
import com.clf.mianshiren.common.ErrorCode;
import com.clf.mianshiren.common.ResultUtils;
import com.clf.mianshiren.constant.HotKeyConstant;
import com.clf.mianshiren.constant.RedisConstant;
import com.clf.mianshiren.constant.UserConstant;
import com.clf.mianshiren.exception.BusinessException;
import com.clf.mianshiren.exception.ThrowUtils;
import com.clf.mianshiren.model.dto.question.*;
import com.clf.mianshiren.model.entity.Question;
import com.clf.mianshiren.model.entity.QuestionBankQuestion;
import com.clf.mianshiren.model.entity.User;
import com.clf.mianshiren.model.vo.QuestionBankVO;
import com.clf.mianshiren.model.vo.QuestionVO;
import com.clf.mianshiren.sentinel.SentinelResourceNameConstant;
import com.clf.mianshiren.service.QuestionBankQuestionService;
import com.clf.mianshiren.service.QuestionService;
import com.clf.mianshiren.service.UserService;
import com.clf.mianshiren.service.impl.QuestionBankServiceImpl;
import com.clf.mianshiren.utils.RedissonManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题目表接口
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private RedissonManager redissonManager;

    // region 增删改查

    /**
     * 创建题目表
     *
     * @param questionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        //  在此处将实体类和 DTO 进行转换
        Question question = new Question();
        List<String> tags = questionAddRequest.getTags();
        BeanUtils.copyProperties(questionAddRequest, question);
        String tagsStr = JSONUtil.toJsonStr(tags);
        // 数据校验
        questionService.validQuestion(question, true);
        //  填充默认值
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        question.setTags(tagsStr);
        // 写入数据库
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newQuestionId = question.getId();
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除题目表
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新题目表（仅管理员可用）
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //  在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        // 数据校验
        questionService.validQuestion(question, false);
        // 判断是否存在
        long id = questionUpdateRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取题目表（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    @HotKeyCache(keyPrefix = HotKeyConstant.QuestionVO, key = "#id", type = QuestionVO.class)
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        // 查询数据库
        Question question = questionService.getById(id);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVO(question, request));
    }

    /**
     * 分页获取题目表列表（仅管理员可用）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest, HttpServletRequest request) {
        // 查询数据库
        Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest, request);
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取题目表列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    @SentinelResource(value = SentinelResourceNameConstant.LIST_QUESTIONVO_BY_PAGE, fallback = "fallbackHandler", blockHandler = "blockExceptionHandler")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        // 获取用户 id
        String ip = request.getRemoteAddr();

        Entry entry = null;

        try {
            // 传入参数，获取到 Entry
            entry = SphU.entry(SentinelResourceNameConstant.LIST_QUESTIONVO_BY_PAGE, EntryType.IN, 1, ip);

            Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest, request);
            // 获取封装类
            return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
        } catch(Throwable ex) {

            // 判断是业务逻辑还是 BlockException，需要上报给 Sentinel
            if(!(ex instanceof BlockException)) {
                // 业务逻辑
                Tracer.trace(ex);
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
            }

            // 判断是降级还是限流
            if(ex instanceof DegradeException) {
                // 降级服务
                return fallbackHandler(questionQueryRequest, request, ex);
            }

            // BlockException
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "服务器繁忙，请稍后再试");

        } finally {
            if(entry != null) {
                entry.exit(1, ip);
            }
        }
    }

//    /**
//     * 待删，这里是为了测试 sentinel 对 ip 细化限流，先不污染原先的 /list/page/vo 接口
//     * @param questionQueryRequest
//     * @param request
//     * @return
//     */
//    @PostMapping("/list/page/vo/sentinel")
//    public BaseResponse<Page<QuestionVO>> listQuestionVOByPageSentinel(@RequestBody QuestionQueryRequest questionQueryRequest,
//                                                               HttpServletRequest request) {
//
//        // 获取用户 id
//        String ip = request.getRemoteAddr();
//
//        Entry entry = null;
//
//        try {
//            // 传入参数，获取到 Entry
//            entry = SphU.entry("listQuestionVOByPage", EntryType.IN, 1, ip);
//
//            Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest, request);
//            // 获取封装类
//            return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
//        } catch(Throwable ex) {
//
//            // 判断是业务逻辑还是 BlockException，需要上报给 Sentinel
//            if(!(ex instanceof BlockException)) {
//                // 业务逻辑
//                Tracer.trace(ex);
//                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
//            }
//
//            // 判断是降级还是限流
//            if(ex instanceof DegradeException) {
//                // 降级服务
//                return fallbackHandler(questionQueryRequest, request, ex);
//            }
//
//            // BlockException
//            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "服务器繁忙，请稍后再试");
//
//        } finally {
//            if(entry != null) {
//                entry.exit(1, ip);
//            }
//        }
//
//    }

    public BaseResponse<Page<QuestionVO>> fallbackHandler(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request, Throwable ex) {
        return ResultUtils.success(null); // 这里可以返回本地缓存
    }

    public BaseResponse<Page<QuestionVO>> blockExceptionHandler(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                          HttpServletRequest request, BlockException ex) {

        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "访问过于频繁，请稍后再试");

    }



    /**
     * 分页获取当前登录用户创建的题目表列表
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 编辑题目表（给用户使用）
     *
     * @param questionEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //  在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        // 数据校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionEditRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @PostMapping("/search/page/vo")
    public BaseResponse<Page<QuestionVO>> searchQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR);

        // 尝试使用令牌桶算法来实现对单个ip的限制，封号处理，做好反爬策略
        redissonManager.tryToInvokeController(RedisConstant.QUESTION_IP_KEY_PREFIX + request.getRemoteAddr(), null, request);

        Page<Question> questionPage = questionService.searchFromEs(questionQueryRequest);
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    @PostMapping("/delete/batch")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> batchDeleteQuestions(@RequestBody QuestionBatchDeleteRequest questionBatchDeleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionBatchDeleteRequest == null, ErrorCode.PARAMS_ERROR);
        questionService.batchDeleteQuestions(questionBatchDeleteRequest.getQuestionIdList());
        return ResultUtils.success(true);
    }


    // endregion
}
