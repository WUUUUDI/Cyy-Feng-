package com.clf.mianshiren.model.dto.question;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author clf
 * @version 1.0
 */
@Data
public class QuestionBatchDeleteRequest implements Serializable {

    private List<Long> questionIdList;

    private static final long serialVersionUID = 1L;


}
