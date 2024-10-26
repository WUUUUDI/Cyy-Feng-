package com.clf.mianshiren.model.dto.questionBankQuestion;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author clf
 * @version 1.0
 */
@Data
public class QuestionBankQuestionBatchRemoveRequest implements Serializable {

    private List<Long> questionIdList;

    private Long questionBankId;

    private static final long serialVersionUID = 1L;
}
