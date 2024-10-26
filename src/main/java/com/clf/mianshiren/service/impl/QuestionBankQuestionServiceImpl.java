package com.clf.mianshiren.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.clf.mianshiren.common.BaseResponse;
import com.clf.mianshiren.common.ErrorCode;
import com.clf.mianshiren.common.ResultUtils;
import com.clf.mianshiren.constant.CommonConstant;
import com.clf.mianshiren.exception.BusinessException;
import com.clf.mianshiren.exception.ThrowUtils;
import com.clf.mianshiren.mapper.QuestionBankQuestionMapper;
import com.clf.mianshiren.model.dto.questionBankQuestion.QuestionBankQuestionQueryRequest;
import com.clf.mianshiren.model.dto.questionBankQuestion.QuestionBankQuestionRemoveRequest;
import com.clf.mianshiren.model.entity.Question;
import com.clf.mianshiren.model.entity.QuestionBank;
import com.clf.mianshiren.model.entity.QuestionBankQuestion;
import com.clf.mianshiren.model.entity.User;
import com.clf.mianshiren.model.vo.QuestionBankQuestionVO;
import com.clf.mianshiren.model.vo.UserVO;
import com.clf.mianshiren.service.QuestionBankQuestionService;
import com.clf.mianshiren.service.QuestionBankService;
import com.clf.mianshiren.service.QuestionService;
import com.clf.mianshiren.service.UserService;
import com.clf.mianshiren.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * 题库题目表服务实现
 *

 */
@Service
@Slf4j
public class QuestionBankQuestionServiceImpl extends ServiceImpl<QuestionBankQuestionMapper, QuestionBankQuestion> implements QuestionBankQuestionService {

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private QuestionService questionService;

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    @Lazy
    private QuestionBankQuestionService questionBankQuestionService;

    // 批量插入线程池
    @Autowired
    @Qualifier("batchAddExecutor")
    private ThreadPoolExecutor batchAddExecutor;

    /**
     * 校验数据
     *
     * @param questionBankQuestion
     * @param add      对创建的数据进行校验
     */
    @Override
    public void validQuestionBankQuestion(QuestionBankQuestion questionBankQuestion, boolean add) {
        ThrowUtils.throwIf(questionBankQuestion == null, ErrorCode.PARAMS_ERROR);
        // 题库id 和 题目id 必须存在，并且在数据库中也可以查到
        Long questionBankId = questionBankQuestion.getQuestionBankId();
        Long questionId = questionBankQuestion.getQuestionId();
        ThrowUtils.throwIf(ObjectUtil.isNull(questionBankId), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(ObjectUtil.isNull(questionId), ErrorCode.PARAMS_ERROR);

        // 从数据库中查看题目和题库是否存在
        Question question = questionService.getById(questionId);
        QuestionBank questionBank = questionBankService.getById(questionBankId);

        if(question == null) {
            ResultUtils.error(ErrorCode.NOT_FOUND_ERROR);
        }

        if(questionBank == null) {
            ResultUtils.error(ErrorCode.NOT_FOUND_ERROR);
        }

    }

    /**
     * 获取查询条件
     *
     * @param questionBankQuestionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest) {
        QueryWrapper<QuestionBankQuestion> queryWrapper = new QueryWrapper<>();
        if (questionBankQuestionQueryRequest == null) {
            return queryWrapper;
        }
        //  从对象中取值
        Long id = questionBankQuestionQueryRequest.getId();
        Long notId = questionBankQuestionQueryRequest.getNotId();
        Long questionBankId = questionBankQuestionQueryRequest.getQuestionBankId();
        Long questionId = questionBankQuestionQueryRequest.getQuestionId();
        Long userId = questionBankQuestionQueryRequest.getUserId();
        String sortField = questionBankQuestionQueryRequest.getSortField();
        String sortOrder = questionBankQuestionQueryRequest.getSortOrder();

        //  补充需要的查询条件
        // 精确查询
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "questionBankId", questionBankId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "questionId", questionId);
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 获取题库题目表封装
     *
     * @param questionBankQuestion
     * @param request
     * @return
     */
    @Override
    public QuestionBankQuestionVO getQuestionBankQuestionVO(QuestionBankQuestion questionBankQuestion, HttpServletRequest request) {
        // 对象转封装类
        QuestionBankQuestionVO questionBankQuestionVO = QuestionBankQuestionVO.objToVo(questionBankQuestion);

        //  可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Long userId = questionBankQuestion.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionBankQuestionVO.setUser(userVO);
        // endregion

        return questionBankQuestionVO;
    }

    /**
     * 分页获取题库题目表封装
     *
     * @param questionBankQuestionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionBankQuestionVO> getQuestionBankQuestionVOPage(Page<QuestionBankQuestion> questionBankQuestionPage, HttpServletRequest request) {
        List<QuestionBankQuestion> questionBankQuestionList = questionBankQuestionPage.getRecords();
        Page<QuestionBankQuestionVO> questionBankQuestionVOPage = new Page<>(questionBankQuestionPage.getCurrent(), questionBankQuestionPage.getSize(), questionBankQuestionPage.getTotal());
        if (CollUtil.isEmpty(questionBankQuestionList)) {
            return questionBankQuestionVOPage;
        }
        // 对象列表 => 封装对象列表
        List<QuestionBankQuestionVO> questionBankQuestionVOList = questionBankQuestionList.stream().map(questionBankQuestion -> {
            return QuestionBankQuestionVO.objToVo(questionBankQuestion);
        }).collect(Collectors.toList());

        //  可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Set<Long> userIdSet = questionBankQuestionList.stream().map(QuestionBankQuestion::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 填充信息
        questionBankQuestionVOList.forEach(questionBankQuestionVO -> {
            Long userId = questionBankQuestionVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            questionBankQuestionVO.setUser(userService.getUserVO(user));
        });
        // endregion

        questionBankQuestionVOPage.setRecords(questionBankQuestionVOList);
        return questionBankQuestionVOPage;
    }

    @Override
    public Boolean removeQuestionBankQuestionByIds(QuestionBankQuestionRemoveRequest questionBankQuestionRemoveRequest, HttpServletRequest request) {

        Long questionBankId = questionBankQuestionRemoveRequest.getQuestionBankId();
        Long questionId = questionBankQuestionRemoveRequest.getQuestionId();
        // 题库 id 和题目 id 都不能为空
        ThrowUtils.throwIf(!ObjectUtil.isAllNotEmpty(questionId, questionBankId), ErrorCode.PARAMS_ERROR);

        // 编写包装类
        QueryWrapper<QuestionBankQuestion> qw = new QueryWrapper<>();

        qw.eq("questionId", questionId)
                .eq("questionBankId", questionBankId);

        return questionBankQuestionService.remove(qw);
    }

    /**
     * 批量添加题目到题库中
     * @param questionIdList
     * @param questionBankId
     * @param loginUser
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAddQuestionsToBank(List<Long> questionIdList, Long questionBankId, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "题目列表为空");
        ThrowUtils.throwIf(questionBankId == null, ErrorCode.PARAMS_ERROR, "题库非法");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 检查题目 id 是否存在，返回存在的 题目id 列表
        LambdaQueryWrapper<Question> questionWrapper = Wrappers.lambdaQuery(Question.class)
                .select(Question::getId)
                .in(Question::getId, questionIdList);
        List<Long> validQuestionIds = questionService.listObjs(questionWrapper, obj -> (Long) obj);

//        List<Question> questionList = questionService.listByIds(questionIdList);
//        List<Long> validQuestionIds = questionList.stream()
//                .map(Question::getId)
//                .collect(Collectors.toList());

        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIds), ErrorCode.PARAMS_ERROR, "合法的题目列表为空");

        // 检查题库 id 是否为空
        QuestionBank hasBank = questionBankService.getById(questionBankId);

        if(hasBank == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "题库不存在");
        }

        // 检查哪些题目还存在于题库中（不在题库中的不要）
        LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
                .in(QuestionBankQuestion::getQuestionId, validQuestionIds);
        List<QuestionBankQuestion> existQuestionList = this.list(lambdaQueryWrapper);
        // 已经存在于题库中的题目 id
        Set<Long> existQuestionIdList = existQuestionList.stream()
                .map(QuestionBankQuestion::getQuestionId)
                .collect(Collectors.toSet());

        // 已经存在于题库中的题目，不需要重复添加
        validQuestionIds = validQuestionIds.stream().filter(questionId -> {
            return !existQuestionIdList.contains(questionId);
        }).collect(Collectors.toList());

        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIds), ErrorCode.PARAMS_ERROR, "所有题目都已经存在于题库中");

        // 调用批量插入题库的方法
        int batchSize = 500; // 批量操作 1000 个题目
        int totalQuestionListSize = validQuestionIds.size();

        for (int i = 0; i < totalQuestionListSize; i+= batchSize) {
            // 生成每批数据
            List<Long> subList = validQuestionIds.subList(i, Math.min(totalQuestionListSize, i + batchSize));
            List<QuestionBankQuestion> questionBankQuestionList = subList.stream().map(questionId -> {
                QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
                questionBankQuestion.setQuestionBankId(questionBankId);
                questionBankQuestion.setQuestionId(questionId);
                questionBankQuestion.setUserId(loginUser.getId());
                return questionBankQuestion;
            }).collect(Collectors.toList());
            // 使用内部方法来批量添加
            QuestionBankQuestionService questionBankQuestionService = (QuestionBankQuestionServiceImpl) AopContext.currentProxy();
            // 提交任务到线程池
            batchAddExecutor.submit(() -> {
                questionBankQuestionService.batchAddQuestionsToBankInner(questionBankQuestionList);
            });
        }

    }


    /**
     * 批量从题库中删除题目
     * @param questionIds
     * @param questionBankId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRemoveQuestionsFromBank(List<Long> questionIds, Long questionBankId) {

        // 校验参数
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIds), ErrorCode.PARAMS_ERROR, "要删除的题目列表为空");
        ThrowUtils.throwIf(questionBankId == null, ErrorCode.PARAMS_ERROR, "题库非法");

        // 执行 sql
        for(Long questionId : questionIds) {

            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .eq(QuestionBankQuestion::getQuestionId, questionId)
                    .eq(QuestionBankQuestion::getQuestionBankId, questionBankId);
            boolean result = this.remove(lambdaQueryWrapper);
            if(!result) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "从题库移除题目失败");
            }

        }

    }


    /**
     * 用于分批插入题目到题库中
     * @param questionBankQuestions
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void batchAddQuestionsToBankInner(List<QuestionBankQuestion> questionBankQuestions) {

        // 细化异常处理
        try {
            System.out.print("开始" + Thread.currentThread().getName());
            boolean result = this.saveBatch(questionBankQuestions);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
            System.out.print("完毕" + Thread.currentThread().getName());
        } catch (DataIntegrityViolationException e) {
            log.error("数据库唯一键冲突或违反其他完整性约束，错误信息: {}",
                    e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已存在于该题库，无法重复添加");
        } catch (DataAccessException e) {
            log.error("数据库连接问题、事务问题等导致操作失败，错误信息: {}",
                    e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
        } catch (Exception e) {
            // 捕获其他异常，做通用处理
            log.error("添加题目到题库时发生未知错误，错误信息: {}",
                    e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
        }

    }

}
