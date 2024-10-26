package com.clf.mianshiren.satoken;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.clf.mianshiren.constant.UserConstant;
import com.clf.mianshiren.model.entity.User;
import org.elasticsearch.common.util.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author clf
 * @version 1.0
 */
@Component
public class StpInterfaceImpl implements StpInterface {
    /**
     * 返回一个用户的权限码（暂时为空，没什么用）
     * @param o
     * @param s
     * @return
     */
    @Override
    public List<String> getPermissionList(Object o, String s) {
        return new ArrayList<>();
    }

    /**
     * 返回一个用户拥有的角色标识
     * @return
     */
    @Override
    public List<String> getRoleList(Object userId, String loginType) {

        User user = (User) StpUtil.getSessionByLoginId(userId).get(UserConstant.USER_LOGIN_STATE);
        if(user == null) {
            throw new NotLoginException("未登录", loginType, "");
        }
        String role = user.getUserRole();

        return CollectionUtils.newSingletonArrayList(role);
    }
}
