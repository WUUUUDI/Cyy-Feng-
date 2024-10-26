package com.clf.mianshiren.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.clf.mianshiren.model.dto.questionBankQuestion.*;
import com.clf.mianshiren.model.entity.QuestionBank;
import com.clf.mianshiren.model.entity.QuestionBankQuestion;
import com.clf.mianshiren.model.entity.User;
import com.clf.mianshiren.model.vo.QuestionBankQuestionVO;
import com.clf.mianshiren.model.vo.QuestionVO;
import com.clf.mianshiren.sentinel.SentinelResourceNameConstant;
import com.clf.mianshiren.service.QuestionBankQuestionService;
import com.clf.mianshiren.service.UserService;
import com.clf.mianshiren.utils.RedissonManager;
import com.clf.mianshiren.utils.ThreadHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 题库题目表接口
 *

 */
@RestController
@RequestMapping("/questionBankQuestion")
@Slf4j
public class QuestionBankQuestionController {

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private UserService userService;

    @Resource
    private RedissonManager redissonManager;

    // region 增删改查

    /**
     * 创建题库题目表
     *
     * @param questionBankQuestionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addQuestionBankQuestion(@RequestBody QuestionBankQuestionAddRequest questionBankQuestionAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankQuestionAddRequest == null, ErrorCode.PARAMS_ERROR);
        //  在此处将实体类和 DTO 进行转换
        QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
        BeanUtils.copyProperties(questionBankQuestionAddRequest, questionBankQuestion);
        // 数据校验
        questionBankQuestionService.validQuestionBankQuestion(questionBankQuestion, true);
        //  填充默认值
        User loginUser = userService.getLoginUser(request);
        questionBankQuestion.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = questionBankQuestionService.save(questionBankQuestion);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newQuestionBankQuestionId = questionBankQuestion.getId();
        return ResultUtils.success(newQuestionBankQuestionId);
    }

    /**
     * 删除题库题目表
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteQuestionBankQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        QuestionBankQuestion oldQuestionBankQuestion = questionBankQuestionService.getById(id);
        ThrowUtils.throwIf(oldQuestionBankQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestionBankQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionBankQuestionService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新题库题目表（仅管理员可用）
     *
     * @param questionBankQuestionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestionBankQuestion(@RequestBody QuestionBankQuestionUpdateRequest questionBankQuestionUpdateRequest) {
        if (questionBankQuestionUpdateRequest == null || questionBankQuestionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //  在此处将实体类和 DTO 进行转换
        QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
        BeanUtils.copyProperties(questionBankQuestionUpdateRequest, questionBankQuestion);
        // 数据校验
        questionBankQuestionService.validQuestionBankQuestion(questionBankQuestion, false);
        // 判断是否存在
        long id = questionBankQuestionUpdateRequest.getId();
        QuestionBankQuestion oldQuestionBankQuestion = questionBankQuestionService.getById(id);
        ThrowUtils.throwIf(oldQuestionBankQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionBankQuestionService.updateById(questionBankQuestion);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取题库题目表（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    @HotKeyCache(keyPrefix = HotKeyConstant.QuestionBankQuestionVO, key = "#id", type = QuestionBankQuestionVO.class)
    public BaseResponse<QuestionBankQuestionVO> getQuestionBankQuestionVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        // 查询数据库
        QuestionBankQuestion questionBankQuestion = questionBankQuestionService.getById(id);
        ThrowUtils.throwIf(questionBankQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(questionBankQuestionService.getQuestionBankQuestionVO(questionBankQuestion, request));
    }

    /**
     * 分页获取题库题目表列表（仅管理员可用）
     *
     * @param questionBankQuestionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<QuestionBankQuestion>> listQuestionBankQuestionByPage(@RequestBody QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest, HttpServletRequest request) {
        long current = questionBankQuestionQueryRequest.getCurrent();
        long size = questionBankQuestionQueryRequest.getPageSize();
        // 查询数据库
        Page<QuestionBankQuestion> questionBankQuestionPage = questionBankQuestionService.page(new Page<>(current, size),
                questionBankQuestionService.getQueryWrapper(questionBankQuestionQueryRequest));
        return ResultUtils.success(questionBankQuestionPage);
    }

    /**
     * 分页获取题库题目表列表（封装类）
     *
     * @param questionBankQuestionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    @SentinelResource(value = SentinelResourceNameConstant.LIST_QUESTIONBANKQUESTIONVO_BY_PAGE, fallback = "handleFallBackHandler", blockHandler = "handleBlockHandler")
    public BaseResponse<Page<QuestionBankQuestionVO>> listQuestionBankQuestionVOByPage(@RequestBody QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest,
                                                               HttpServletRequest request) {
        long current = questionBankQuestionQueryRequest.getCurrent();
        long size = questionBankQuestionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 获取用户 id
        String ip = request.getRemoteAddr();

        Entry entry = null;

        try {
            // 传入参数，获取到 Entry
            entry = SphU.entry(SentinelResourceNameConstant.LIST_QUESTIONBANKQUESTIONVO_BY_PAGE, EntryType.IN, 1, ip);

            // 查询数据库
            Page<QuestionBankQuestion> questionBankQuestionPage = questionBankQuestionService.page(new Page<>(current, size),
                    questionBankQuestionService.getQueryWrapper(questionBankQuestionQueryRequest));
            // 获取封装类
            return ResultUtils.success(questionBankQuestionService.getQuestionBankQuestionVOPage(questionBankQuestionPage, request));
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
                return handleFallBackHandler(questionBankQuestionQueryRequest, request, ex);
            }

            // BlockException
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "服务器繁忙，请稍后再试");

        } finally {
            if(entry != null) {
                entry.exit(1, ip);
            }
        }

    }

    public BaseResponse<Page<QuestionBankQuestionVO>> handleFallBackHandler(@RequestBody QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest,
                                                                                               HttpServletRequest request, Throwable ex) {
        return ResultUtils.success(null);
    }

    public BaseResponse<Page<QuestionBankQuestionVO>> handleBlockHandler(@RequestBody QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest,
                                                                            HttpServletRequest request, Exception ex) {
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "访问过于频繁，请稍后再试");

    }

    /**
     * 分页获取当前登录用户创建的题库题目表列表
     *
     * @param questionBankQuestionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionBankQuestionVO>> listMyQuestionBankQuestionVOByPage(@RequestBody QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankQuestionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionBankQuestionQueryRequest.setUserId(loginUser.getId());
        long current = questionBankQuestionQueryRequest.getCurrent();
        long size = questionBankQuestionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<QuestionBankQuestion> questionBankQuestionPage = questionBankQuestionService.page(new Page<>(current, size),
                questionBankQuestionService.getQueryWrapper(questionBankQuestionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionBankQuestionService.getQuestionBankQuestionVOPage(questionBankQuestionPage, request));
    }

    // endregion

    @PostMapping("/remove")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> removeQuestionBankQuestionByIds(@RequestBody QuestionBankQuestionRemoveRequest questionBankQuestionRemoveRequest, HttpServletRequest request) {

        Boolean removed = questionBankQuestionService.removeQuestionBankQuestionByIds(questionBankQuestionRemoveRequest, request);

        return ResultUtils.success(removed);
    }

    /**
     * 批量添加题目到题库
     * @param questionBankQuestionBatchAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add/batch")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> batchAddQuestionsToBank(@RequestBody QuestionBankQuestionBatchAddRequest questionBankQuestionBatchAddRequest, HttpServletRequest request) {

        // 参数校验
        ThrowUtils.throwIf(questionBankQuestionBatchAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long questionBankId = questionBankQuestionBatchAddRequest.getQuestionBankId();
        List<Long> questionIdList = questionBankQuestionBatchAddRequest.getQuestionIdList();
        questionBankQuestionService.batchAddQuestionsToBank(questionIdList, questionBankId, loginUser);
        return ResultUtils.success(true);
    }

    @PostMapping("/remove/batch")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> batchRemoveQuestionsFromBank(@RequestBody QuestionBankQuestionBatchRemoveRequest questionBankQuestionBatchRemoveRequest, HttpServletRequest request) {

        // 参数校验
        ThrowUtils.throwIf(questionBankQuestionBatchRemoveRequest == null, ErrorCode.PARAMS_ERROR);
        Long questionBankId = questionBankQuestionBatchRemoveRequest.getQuestionBankId();
        List<Long> questionIdList = questionBankQuestionBatchRemoveRequest.getQuestionIdList();
        questionBankQuestionService.batchRemoveQuestionsFromBank(questionIdList, questionBankId);
        return ResultUtils.success(true);
    }





}
