package com.clf.mianshiren.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.clf.mianshiren.common.ErrorCode;
import com.clf.mianshiren.constant.CommonConstant;
import com.clf.mianshiren.exception.ThrowUtils;
import com.clf.mianshiren.mapper.QuestionBankMapper;
import com.clf.mianshiren.model.dto.questionBank.QuestionBankQueryRequest;
import com.clf.mianshiren.model.entity.QuestionBank;

import com.clf.mianshiren.model.entity.User;
import com.clf.mianshiren.model.vo.QuestionBankVO;
import com.clf.mianshiren.model.vo.UserVO;
import com.clf.mianshiren.service.QuestionBankService;
import com.clf.mianshiren.service.UserService;
import com.clf.mianshiren.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题库表服务实现
 *

 */
@Service
@Slf4j
public class QuestionBankServiceImpl extends ServiceImpl<QuestionBankMapper, QuestionBank> implements QuestionBankService {

    @Resource
    private UserService userService;

    /**
     * 校验数据
     *
     * @param questionBank
     * @param add      对创建的数据进行校验
     */
    @Override
    public void validQuestionBank(QuestionBank questionBank, boolean add) {
        ThrowUtils.throwIf(questionBank == null, ErrorCode.PARAMS_ERROR);
        //  从对象中取值
        String title = questionBank.getTitle();
        String description = questionBank.getDescription();
        // 创建数据时，参数不能为空
        if (add) {
            //  补充校验规则
            ThrowUtils.throwIf(StringUtils.isAnyBlank(title, description), ErrorCode.PARAMS_ERROR);
        }
        // 修改数据时，有参数则校验
        //  补充校验规则
        if (StringUtils.isAnyBlank(title, description)) {
            ThrowUtils.throwIf(title.length() > 80, ErrorCode.PARAMS_ERROR, "标题过长");
            ThrowUtils.throwIf(description.length() > 10000, ErrorCode.PARAMS_ERROR, "内容过长");
        }
    }

    /**
     * 获取查询条件
     *
     * @param questionBankQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<QuestionBank> getQueryWrapper(QuestionBankQueryRequest questionBankQueryRequest) {
        QueryWrapper<QuestionBank> queryWrapper = new QueryWrapper<>();
        if (questionBankQueryRequest == null) {
            return queryWrapper;
        }
        //  从对象中取值
        Long id = questionBankQueryRequest.getId();
        Long notId = questionBankQueryRequest.getNotId();
        String title = questionBankQueryRequest.getTitle();
        String description = questionBankQueryRequest.getDescription();
        String searchText = questionBankQueryRequest.getSearchText();
        String sortField = questionBankQueryRequest.getSortField();
        String sortOrder = questionBankQueryRequest.getSortOrder();
        Long userId = questionBankQueryRequest.getUserId();
        //  补充需要的查询条件
        // 从多字段中搜索
        if (StringUtils.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("title", searchText).or().like("content", searchText));
        }
        // 模糊查询
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(description), "description", description);
        // JSON 数组查询
//        if (CollUtil.isNotEmpty(tagList)) {
//            for (String tag : tagList) {
//                queryWrapper.like("tags", "\"" + tag + "\"");
//            }
//        }
        // 精确查询
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 获取题库表封装
     *
     * @param questionBank
     * @param request
     * @return
     */
    @Override
    public QuestionBankVO getQuestionBankVO(QuestionBank questionBank, HttpServletRequest request) {
        // 对象转封装类
        QuestionBankVO questionBankVO = QuestionBankVO.objToVo(questionBank);

        //  可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Long userId = questionBank.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionBankVO.setUser(userVO);
//        // 2. 已登录，获取用户点赞、收藏状态
//        long questionBankId = questionBank.getId();
//        User loginUser = userService.getLoginUserPermitNull(request);
//        if (loginUser != null) {
//            // 获取点赞
//            QueryWrapper<QuestionBankThumb> questionBankThumbQueryWrapper = new QueryWrapper<>();
//            questionBankThumbQueryWrapper.in("questionBankId", questionBankId);
//            questionBankThumbQueryWrapper.eq("userId", loginUser.getId());
//            QuestionBankThumb questionBankThumb = questionBankThumbMapper.selectOne(questionBankThumbQueryWrapper);
//            questionBankVO.setHasThumb(questionBankThumb != null);
//            // 获取收藏
//            QueryWrapper<QuestionBankFavour> questionBankFavourQueryWrapper = new QueryWrapper<>();
//            questionBankFavourQueryWrapper.in("questionBankId", questionBankId);
//            questionBankFavourQueryWrapper.eq("userId", loginUser.getId());
//            QuestionBankFavour questionBankFavour = questionBankFavourMapper.selectOne(questionBankFavourQueryWrapper);
//            questionBankVO.setHasFavour(questionBankFavour != null);
//        }
        // endregion

        return questionBankVO;
    }

    /**
     * 分页获取题库表封装
     *
     * @param questionBankPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionBankVO> getQuestionBankVOPage(Page<QuestionBank> questionBankPage, HttpServletRequest request) {
        List<QuestionBank> questionBankList = questionBankPage.getRecords();
        Page<QuestionBankVO> questionBankVOPage = new Page<>(questionBankPage.getCurrent(), questionBankPage.getSize(), questionBankPage.getTotal());
        if (CollUtil.isEmpty(questionBankList)) {
            return questionBankVOPage;
        }
        // 对象列表 => 封装对象列表
        List<QuestionBankVO> questionBankVOList = questionBankList.stream().map(questionBank -> {
            return QuestionBankVO.objToVo(questionBank);
        }).collect(Collectors.toList());

        //  可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Set<Long> userIdSet = questionBankList.stream().map(QuestionBank::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
//        // 2. 已登录，获取用户点赞、收藏状态
//        Map<Long, Boolean> questionBankIdHasThumbMap = new HashMap<>();
//        Map<Long, Boolean> questionBankIdHasFavourMap = new HashMap<>();
//        User loginUser = userService.getLoginUserPermitNull(request);
//        if (loginUser != null) {
//            Set<Long> questionBankIdSet = questionBankList.stream().map(QuestionBank::getId).collect(Collectors.toSet());
//            loginUser = userService.getLoginUser(request);
//            // 获取点赞
//            QueryWrapper<QuestionBankThumb> questionBankThumbQueryWrapper = new QueryWrapper<>();
//            questionBankThumbQueryWrapper.in("questionBankId", questionBankIdSet);
//            questionBankThumbQueryWrapper.eq("userId", loginUser.getId());
//            List<QuestionBankThumb> questionBankQuestionBankThumbList = questionBankThumbMapper.selectList(questionBankThumbQueryWrapper);
//            questionBankQuestionBankThumbList.forEach(questionBankQuestionBankThumb -> questionBankIdHasThumbMap.put(questionBankQuestionBankThumb.getQuestionBankId(), true));
//            // 获取收藏
//            QueryWrapper<QuestionBankFavour> questionBankFavourQueryWrapper = new QueryWrapper<>();
//            questionBankFavourQueryWrapper.in("questionBankId", questionBankIdSet);
//            questionBankFavourQueryWrapper.eq("userId", loginUser.getId());
//            List<QuestionBankFavour> questionBankFavourList = questionBankFavourMapper.selectList(questionBankFavourQueryWrapper);
//            questionBankFavourList.forEach(questionBankFavour -> questionBankIdHasFavourMap.put(questionBankFavour.getQuestionBankId(), true));
//        }
        // 填充信息
        questionBankVOList.forEach(questionBankVO -> {
            Long userId = questionBankVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            questionBankVO.setUser(userService.getUserVO(user));
//            questionBankVO.setHasThumb(questionBankIdHasThumbMap.getOrDefault(questionBankVO.getId(), false));
//            questionBankVO.setHasFavour(questionBankIdHasFavourMap.getOrDefault(questionBankVO.getId(), false));
        });
        // endregion

        questionBankVOPage.setRecords(questionBankVOList);
        return questionBankVOPage;
    }

}
