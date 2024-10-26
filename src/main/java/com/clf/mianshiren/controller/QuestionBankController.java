package com.clf.mianshiren.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
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
import com.clf.mianshiren.model.dto.question.QuestionQueryRequest;
import com.clf.mianshiren.model.dto.questionBank.QuestionBankAddRequest;
import com.clf.mianshiren.model.dto.questionBank.QuestionBankEditRequest;
import com.clf.mianshiren.model.dto.questionBank.QuestionBankQueryRequest;
import com.clf.mianshiren.model.dto.questionBank.QuestionBankUpdateRequest;
import com.clf.mianshiren.model.entity.Question;
import com.clf.mianshiren.model.entity.QuestionBank;
import com.clf.mianshiren.model.entity.User;
import com.clf.mianshiren.model.vo.QuestionBankVO;
import com.clf.mianshiren.model.vo.QuestionVO;
import com.clf.mianshiren.sentinel.SentinelResourceNameConstant;
import com.clf.mianshiren.service.QuestionBankService;
import com.clf.mianshiren.service.QuestionService;
import com.clf.mianshiren.service.UserService;
import com.clf.mianshiren.utils.RedissonManager;
import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 题库表接口
 */
@RestController
@RequestMapping("/questionBank")
@Slf4j
public class QuestionBankController {

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    private UserService userService;

    @Resource
    private QuestionService questionService;

    @Resource
    private RedissonManager redissonManager;

    // region 增删改查

    /**
     * 创建题库表
     *
     * @param questionBankAddRequest
     * @param request
     * @return
     */
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @PostMapping("/add")
    public BaseResponse<Long> addQuestionBank(@RequestBody QuestionBankAddRequest questionBankAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankAddRequest == null, ErrorCode.PARAMS_ERROR);
        //  在此处将实体类和 DTO 进行转换
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankAddRequest, questionBank);
        // 数据校验
        questionBankService.validQuestionBank(questionBank, true);
        //  填充默认值
        User loginUser = userService.getLoginUser(request);
        questionBank.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = questionBankService.save(questionBank);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newQuestionBankId = questionBank.getId();
        return ResultUtils.success(newQuestionBankId);
    }

    /**
     * 删除题库表（仅管理员）
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestionBank(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 利用 Sa-Token 得到用户
        User user = (User) StpUtil.getSession().get(UserConstant.USER_LOGIN_STATE);

        long id = deleteRequest.getId();
        // 判断是否存在
        QuestionBank oldQuestionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestionBank.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionBankService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新题库表（仅管理员可用）
     *
     * @param questionBankUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestionBank(@RequestBody QuestionBankUpdateRequest questionBankUpdateRequest) {
        if (questionBankUpdateRequest == null || questionBankUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //  在此处将实体类和 DTO 进行转换
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankUpdateRequest, questionBank);
        // 数据校验
        questionBankService.validQuestionBank(questionBank, false);
        // 判断是否存在
        long id = questionBankUpdateRequest.getId();
        QuestionBank oldQuestionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionBankService.updateById(questionBank);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取题库表（封装类）
     *
     * @param
     * @return
     */
    @PostMapping("/get/vo")
    @HotKeyCache(keyPrefix = HotKeyConstant.QuestionBankVO, key = "#questionBankQueryRequest.id", type = QuestionBankVO.class)
    public BaseResponse<QuestionBankVO> getQuestionBankVOById(@RequestBody QuestionBankQueryRequest questionBankQueryRequest, HttpServletRequest request) {

        ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = questionBankQueryRequest.getId();
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        boolean isNeed = questionBankQueryRequest.isNeedQueryQuestionList();

        // 查询数据库
        QuestionBank questionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR);

        QuestionBankVO questionBankVO = BeanUtil.copyProperties(questionBank, QuestionBankVO.class);

        // 查看是否需要分页question
        if (isNeed) {
            QuestionQueryRequest questionQueryRequest = new QuestionQueryRequest();
            questionQueryRequest.setQuestionBankId(id);
            Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest, request);
            questionBankVO.setQuestionPage(questionPage);
        }


        // 获取封装类
        return ResultUtils.success(questionBankVO);
    }

//    /**
//     * 根据 id 获取题库表（封装类）
//     *
//     * @param
//     * @return
//     */
//    @PostMapping("/get/vo")
//    public BaseResponse<QuestionBankVO> getQuestionBankVOById(@RequestBody QuestionBankQueryRequest questionBankQueryRequest, HttpServletRequest request) {
//
//        ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
//        Long id = questionBankQueryRequest.getId();
//        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
//        boolean isNeed = questionBankQueryRequest.isNeedQueryQuestionList();
//
//        // 生成 key
//        String key = "bank_detail_" + id;
//        // 如果是热 key
//        if (JdHotKeyStore.isHotKey(key)) {
//            Object cachedQuestionBankVO = JdHotKeyStore.get(key);
//            if (cachedQuestionBankVO != null) {
//                return ResultUtils.success((QuestionBankVO) cachedQuestionBankVO);
//            }
//        }
//
//        // 查询数据库
//        QuestionBank questionBank = questionBankService.getById(id);
//        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR);
//
//        QuestionBankVO questionBankVO = BeanUtil.copyProperties(questionBank, QuestionBankVO.class);
//
//        // 查看是否需要分页question
//        if (isNeed) {
//            QuestionQueryRequest questionQueryRequest = new QuestionQueryRequest();
//            questionQueryRequest.setQuestionBankId(id);
//            Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest, request);
//            questionBankVO.setQuestionPage(questionPage);
//
//        }
//
//        // 设置本地缓存
//        JdHotKeyStore.smartSet(key, questionBankVO);
//
//        // 获取封装类
//        return ResultUtils.success(questionBankVO);
//    }

    /**
     * 分页获取题库表列表（仅管理员可用）
     *
     * @param questionBankQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<QuestionBank>> listQuestionBankByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest) {
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        // 查询数据库
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                questionBankService.getQueryWrapper(questionBankQueryRequest));
        return ResultUtils.success(questionBankPage);
    }

    /**
     * 分页获取题库表列表（封装类）
     *
     * @param questionBankQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    @SentinelResource(fallback = "handleFallback", blockHandler = "handleBlockException", value = SentinelResourceNameConstant.LIST_QUESTIONBANKVO_BY_PAGE)
    public BaseResponse<Page<QuestionBankVO>> listQuestionBankVOByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                                       HttpServletRequest request) {
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 获取用户 id
        String ip = request.getRemoteAddr();

        Entry entry = null;

        try {
            // 传入参数，获取到 Entry
            entry = SphU.entry(SentinelResourceNameConstant.LIST_QUESTIONBANKVO_BY_PAGE, EntryType.IN, 1, ip);

            // 查询数据库
            Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                    questionBankService.getQueryWrapper(questionBankQueryRequest));
            // 获取封装类
            return ResultUtils.success(questionBankService.getQuestionBankVOPage(questionBankPage, request));
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
                return handleFallback(questionBankQueryRequest, request, ex);
            }

            // BlockException
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "服务器繁忙，请稍后再试");

        } finally {
            if(entry != null) {
                entry.exit(1, ip);
            }
        }

    }

    /**
     * listQuestionBankVOByPage 降级操作：直接返回本地数据
     */
    public BaseResponse<Page<QuestionBankVO>> handleFallback(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                             HttpServletRequest request, Throwable ex) {
        // 可以返回本地数据或空数据
        return ResultUtils.success(null);
    }

    /**
     * listQuestionBankVOByPage 流控操作
     * 限流：提示“系统压力过大，请耐心等待”
     */
    public BaseResponse<Page<QuestionBankVO>> handleBlockException(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                                   HttpServletRequest request, BlockException ex) {
        // 限流操作
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统压力过大，请耐心等待");
    }


    /**
     * 分页获取当前登录用户创建的题库表列表
     *
     * @param questionBankQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionBankVO>> listMyQuestionBankVOByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionBankQueryRequest.setUserId(loginUser.getId());
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                questionBankService.getQueryWrapper(questionBankQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionBankService.getQuestionBankVOPage(questionBankPage, request));
    }

    /**
     * 编辑题库表（给用户使用）
     *
     * @param questionBankEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestionBank(@RequestBody QuestionBankEditRequest questionBankEditRequest, HttpServletRequest request) {
        if (questionBankEditRequest == null || questionBankEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //  在此处将实体类和 DTO 进行转换
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankEditRequest, questionBank);
        // 数据校验
        questionBankService.validQuestionBank(questionBank, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionBankEditRequest.getId();
        QuestionBank oldQuestionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestionBank.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionBankService.updateById(questionBank);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    // endregion
}
