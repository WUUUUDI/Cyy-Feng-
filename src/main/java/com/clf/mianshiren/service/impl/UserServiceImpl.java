package com.clf.mianshiren.service.impl;

import static com.clf.mianshiren.constant.UserConstant.USER_LOGIN_STATE;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.db.sql.SqlUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.clf.mianshiren.common.ErrorCode;
import com.clf.mianshiren.config.RedissonConfig;
import com.clf.mianshiren.constant.CommonConstant;
import com.clf.mianshiren.constant.RedisConstant;
import com.clf.mianshiren.constant.UserConstant;
import com.clf.mianshiren.exception.BusinessException;
import com.clf.mianshiren.mapper.UserMapper;
import com.clf.mianshiren.model.dto.user.UserQueryRequest;
import com.clf.mianshiren.model.entity.User;
import com.clf.mianshiren.model.enums.UserRoleEnum;
import com.clf.mianshiren.model.vo.LoginUserVO;
import com.clf.mianshiren.model.vo.UserVO;
import com.clf.mianshiren.satoken.DeviceUtils;
import com.clf.mianshiren.satoken.StpInterfaceImpl;
import com.clf.mianshiren.service.UserService;
import com.clf.mianshiren.utils.SqlUtils;

import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBitSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
 * 用户服务实现
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 盐值，混淆密码
     */
    public static final String SALT = "clf";

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StpInterfaceImpl stpInterface;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        synchronized (userAccount.intern()) {
            // 账户不能重复
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            // 2. 加密
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            // 3. 插入数据
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPassword);
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            return user.getId();
        }
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 用户已被封禁，直接拒绝下面的操作
        if (UserConstant.BAN_ROLE.equals(user.getUserRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "您已被暂时系统封禁");
        }

        // 3. 记录用户的登录态, 改造成 Sa-Token 的方式
        StpUtil.login(user.getId(), DeviceUtils.getRequestDevice(request));
        StpUtil.getSession().set(USER_LOGIN_STATE, user);

        return this.getLoginUserVO(user);
    }

    @Override
    public LoginUserVO userLoginByMpOpen(WxOAuth2UserInfo wxOAuth2UserInfo, HttpServletRequest request) {
        String unionId = wxOAuth2UserInfo.getUnionId();
        String mpOpenId = wxOAuth2UserInfo.getOpenid();
        // 单机锁
        synchronized (unionId.intern()) {
            // 查询用户是否已存在
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("unionId", unionId);
            User user = this.getOne(queryWrapper);
            // 被封号，禁止登录
            if (user != null && UserRoleEnum.BAN.getValue().equals(user.getUserRole())) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该用户已被封，禁止登录");
            }
            // 用户不存在则创建
            if (user == null) {
                user = new User();
                user.setUnionId(unionId);
                user.setMpOpenId(mpOpenId);
                user.setUserAvatar(wxOAuth2UserInfo.getHeadImgUrl());
                user.setUserName(wxOAuth2UserInfo.getNickname());
                boolean result = this.save(user);
                if (!result) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "登录失败");
                }
            }
            // 记录用户的登录态
            request.getSession().setAttribute(USER_LOGIN_STATE, user);
            return getLoginUserVO(user);
        }
    }

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录，利用 Sa-Token
        Object loginUserId = StpUtil.getLoginIdDefaultNull();

        if (loginUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

//        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = this.getById((String) loginUserId);

        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUserPermitNull(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            return null;
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        return this.getById(userId);
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询, 使用 sa-token
        Object loginUserId = StpUtil.getSession().get(USER_LOGIN_STATE);
//        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);

        if (loginUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        User user = this.getById((String) loginUserId);

        return isAdmin(user);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
//        if (request.getSession().getAttribute(USER_LOGIN_STATE) == null) {
//            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
//        }
//        // 移除登录态
//        request.getSession().removeAttribute(USER_LOGIN_STATE);

        // 如果没登陆，则抛出未登陆异常
        StpUtil.checkLogin();

        StpUtil.logout();

        return true;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String unionId = userQueryRequest.getUnionId();
        String mpOpenId = userQueryRequest.getMpOpenId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(unionId), "unionId", unionId);
        queryWrapper.eq(StringUtils.isNotBlank(mpOpenId), "mpOpenId", mpOpenId);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 添加用户签到记录
     *
     * @param userId
     * @return 当前签到是否成功
     */
    @Override
    public boolean addUserSignIn(long userId) {

        LocalDate localDate = LocalDate.now();
        int offset = localDate.getDayOfYear();

        int year = localDate.getYear();

        String key = RedisConstant.getUserSignInRedisKeyPrefix(year, userId);

        RBitSet bitSet = redissonClient.getBitSet(key);


        boolean has = bitSet.get(offset);

        if (!has) {
            return bitSet.set(offset, true);
        }

        return true;
    }

    /**
     * 得到用户签到记录
     *
     * @param year
     * @param userId
     * @return
     */
    @Override
    public List<Integer> getUserSignInRecord(Integer year, long userId) {

        if (year == null) {
            year = LocalDate.now().getYear();
        }

        // 根据 userId 和 year 获取到对应的 bitmap
        RBitSet signInRBitSet = redissonClient.getBitSet(RedisConstant.getUserSignInRedisKeyPrefix(year, userId));
        BitSet signInBitSet = signInRBitSet.asBitSet();

        // 获取当前年份的总天数
        int totalDays = Year.of(year).length();
        List<Integer> result = new ArrayList<>();
        int idx = 0;
        // 从 0 开始找下一个是 1 的位置
        idx = signInBitSet.nextSetBit(0);

        while (idx >= 0) {
            result.add(idx);
            idx = signInBitSet.nextSetBit(idx + 1);
        }

        return result;
    }
}
