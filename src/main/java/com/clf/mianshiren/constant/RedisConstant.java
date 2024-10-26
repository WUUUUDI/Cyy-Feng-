package com.clf.mianshiren.constant;

/**
 * @author clf
 * @version 1.0
 */
public interface RedisConstant {

    /**
     * 用户签到的记录的 key 前缀
     */
    String USER_SIGN_IN_REDIS_KEY_PREFIX = "user:signins";

    /**
     * QuestionBank 的ip限流 key 前缀
     */
    String QUESTION_BANK_IP_KEY_PREFIX = "questionbank:visit:ip:";

    /**
     * QuestionBank 的用户限流 key 前缀
     */
    String QUESTION_BANK_USER_KEY_PREFIX = "questionbank:visit:user:";

    /**
     * Question 的ip限流 key 前缀
     */
    String QUESTION_IP_KEY_PREFIX = "question:visit:ip:";

    /**
     * Question 的用户限流 key 前缀
     */
    String QUESTION_USER_KEY_PREFIX = "question:visit:user:";

    /**
     * QuestionBankQuestion 的ip限流 key 前缀
     */
    String QUESTION_BANK_QUESTION_IP_KEY_PREFIX = "questionbankquestion:visit:ip:";

    /**
     * QuestionBankQuestion 的用户限流 key 前缀
     */
    String QUESTION_BANK_QUESTION_USER_KEY_PREFIX = "questionbankquestion:visit:user:";


    /**
     * 获取用户签到记录的 redis key
     */
    static String getUserSignInRedisKeyPrefix(int year, long userId) {
        return String.format("%s:%s:%s", USER_SIGN_IN_REDIS_KEY_PREFIX, year, userId);
    }

}
