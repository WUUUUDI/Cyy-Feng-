package com.clf.mianshiren.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.clf.mianshiren.model.dto.question.QuestionQueryRequest;
import com.clf.mianshiren.model.entity.Question;
import com.clf.mianshiren.model.entity.QuestionBankQuestion;
import com.clf.mianshiren.model.vo.QuestionVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题目表服务
 *

 */
public interface QuestionService extends IService<Question> {

    /**
     * 从 ES 查询题目
     *
     * @param questionQueryRequest
     * @return
     */
    Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest);

    /**
     * 校验数据
     *
     * @param question
     * @param add 对创建的数据进行校验
     */
    void validQuestion(Question question, boolean add);

    /**
     * 获取查询条件
     *
     * @param questionQueryRequest
     * @return
     */
    QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest);
    
    /**
     * 获取题目表封装
     *
     * @param question
     * @param request
     * @return
     */
    QuestionVO getQuestionVO(Question question, HttpServletRequest request);

    /**
     * 分页获取题目表封装
     *
     * @param questionPage
     * @param request
     * @return
     */
    Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request);


    Page<Question> listQuestionByPage(QuestionQueryRequest questionQueryRequest, HttpServletRequest request);


    /**
     * 批量删除题目
     * @param questionIdList
     */
    void batchDeleteQuestions(List<Long> questionIdList);
}
