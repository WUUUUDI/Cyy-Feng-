package com.clf.mianshiren.email;

import cn.hutool.json.JSONUtil;
import com.clf.mianshiren.common.BaseResponse;
import com.clf.mianshiren.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.clf.mianshiren.common.ResultUtils.success;

/**
 * @author clf
 * @version 1.0
 */
@Slf4j
@Component
public class SendEmailManager {

    // 校验码，qq邮箱获取
    private final String code = "xxxxxxx";

    // 初始化线程池
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
            4,
            8,
            60,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(20),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 生成验证码
     * @return
     */
    public String achieveCode() {  //由于数字 1 、 0 和字母 O 、l 有时分不清楚，所以，没有数字 1 、 0
        String[] beforeShuffle= new String[] { "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F",
                "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "a",
                "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v",
                "w", "x", "y", "z" };
        List list = Arrays.asList(beforeShuffle);//将数组转换为集合
        Collections.shuffle(list);  //打乱集合顺序
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i)); //将集合转化为字符串
        }
        return sb.toString().substring(3, 9);  //截取字符串第4到8
    }


    /**
     * 发送邮件
     * @param email
     * @param session
     * @return
     * @throws EmailException
     */
    @GetMapping("/getotp/{email}")
    public BaseResponse<String> getOtp(@PathVariable String email, HttpSession session) throws EmailException {
        String otpCode = this.achieveCode();
        /* session.setAttribute(email, otpCode); */
        HtmlEmail emails=new HtmlEmail();
        emails.setHostName("smtp.qq.com");
        emails.setCharset("utf-8");
        emails.setSmtpPort(465);
        emails.setSSLOnConnect(true);
        emails.addTo(email);//设置收件人
        emails.setFrom("xxxx@qq.com","xxxx");
        emails.setAuthentication("xxxx@qq.com",code);
        emails.setSubject("主体验证码");//设置发送主题
        emails.setMsg(achieveCode());//设置发送内容
        emails.send();//进行发送
        return success(otpCode);
    }

    /**
     * 提交发送邮件的任务
     * @param user
     * @param request
     * @throws EmailException
     */
    public void sendBanMsgToAdminTask(User user, HttpServletRequest request) throws EmailException {
        executor.submit(() -> {
           try {
               sendBanMsgToAdmin(user, request);
           } catch (Exception e) {
               log.info("异步发送邮箱出现错误，错误信息：{}", e.getMessage());
           }
        });
    }

    /**
     * 发送警告邮件（比如哪一个用户正在爬虫）
     * @return
     * @throws EmailException
     */
    public void sendBanMsgToAdmin(User user, HttpServletRequest request) throws EmailException {
        String email = "xxxx@qq.com";
        /* session.setAttribute(email, otpCode); */
        HtmlEmail emails=new HtmlEmail();
        emails.setHostName("smtp.qq.com");
        emails.setCharset("utf-8");
        emails.setSmtpPort(465);
        emails.setSSLOnConnect(true);
        emails.addTo(email);//设置收件人
        emails.setFrom("xxxx@qq.com","xxxx");
        emails.setAuthentication("xxxx@qq.com",code);
        emails.setSubject("网站正在被爬虫");//设置发送主题
        emails.setMsg(generatorBanMsgByUser(user, request));//设置发送内容
        emails.send();//进行发送
    }

    /**
     * 根据用户信息生成警告内容告知管理员
     * @param user
     * @return
     */
    public String generatorBanMsgByUser(User user, HttpServletRequest request) {

        StringBuilder sb = new StringBuilder();

        Long id = user.getId();
        String userAccount = user.getUserAccount();
        String userName = user.getUserName();
        String userRole = user.getUserRole();

        sb.append("用户name：");
        sb.append(userName).append("\n");
        sb.append("用户id：");
        sb.append(id).append("\n");
        sb.append("用户account：");
        sb.append(userAccount).append("\n");
        sb.append("用户Role：");
        sb.append(userRole).append("\n").append("\n");
        sb.append("发送请求的ip：");
        sb.append(request.getRemoteAddr()).append("\n");
        sb.append("正在访问的uri：");
        sb.append(request.getRequestURI()).append("\n");
        sb.append("请求的参数是：");
        sb.append(JSONUtil.toJsonStr(request)).append("\n");

        return sb.toString();

    }




}
