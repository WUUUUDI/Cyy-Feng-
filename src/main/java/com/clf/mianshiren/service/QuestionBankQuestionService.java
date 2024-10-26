package com.clf.mianshiren.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.clf.mianshiren.common.BaseResponse;
import com.clf.mianshiren.model.dto.questionBankQuestion.QuestionBankQuestionQueryRequest;
import com.clf.mianshiren.model.dto.questionBankQuestion.QuestionBankQuestionRemoveRequest;
import com.clf.mianshiren.model.entity.QuestionBankQuestion;
import com.clf.mianshiren.model.entity.User;
import com.clf.mianshiren.model.vo.QuestionBankQuestionVO;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 题库题目表服务
 *

 */
public interface QuestionBankQuestionService extends IService<QuestionBankQuestion> {

    /**
     * 校验数据
     *
     * @param questionBankQuestion
     * @param add 对创建的数据进行校验
     */
    void validQuestionBankQuestion(QuestionBankQuestion questionBankQuestion, boolean add);

    /**
     * 获取查询条件
     *
     * @param questionBankQuestionQueryRequest
     * @return
     */
    QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest);
    
    /**
     * 获取题库题目表封装
     *
     * @param questionBankQuestion
     * @param request
     * @return
     */
    QuestionBankQuestionVO getQuestionBankQuestionVO(QuestionBankQuestion questionBankQuestion, HttpServletRequest request);

    /**
     * 分页获取题库题目表封装
     *
     * @param questionBankQuestionPage
     * @param request
     * @return
     */
    Page<QuestionBankQuestionVO> getQuestionBankQuestionVOPage(Page<QuestionBankQuestion> questionBankQuestionPage, HttpServletRequest request);


    /**
     * 删除题库题目管链表中的一个记录
     * @param questionBankQuestionRemoveRequest
     * @param request
     * @return
     */
    Boolean removeQuestionBankQuestionByIds(@RequestBody QuestionBankQuestionRemoveRequest questionBankQuestionRemoveRequest, HttpServletRequest request);

    /**
     * 批量向题库添加题目
     * @param questionIdList
     * @param questionBankId
     * @param loginUser
     */
    void batchAddQuestionsToBank(List<Long> questionIdList, Long questionBankId, User loginUser);

    /**
     * 批量从题库移除题目
     * @param questionIds
     * @param questionBankId
     */
    void batchRemoveQuestionsFromBank(List<Long> questionIds, Long questionBankId);

    /**
     * 长事务分批添加题目到题库
     * @param questionBankQuestions
     */
    void batchAddQuestionsToBankInner(List<QuestionBankQuestion> questionBankQuestions);
}
