package com.clf.mianshiren;

import com.clf.mianshiren.esdao.QuestionEsDao;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
public class ElasticsearchRestTemplateTest {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    private static final String INDEX_NAME = "test_index";
    private static final String DOCUMENT_ID = "1";

    @BeforeEach
    public void setup() {
        // 清除索引，重新创建索引
        if (elasticsearchRestTemplate.indexOps(IndexCoordinates.of(INDEX_NAME)).exists()) {
            elasticsearchRestTemplate.indexOps(IndexCoordinates.of(INDEX_NAME)).delete();
        }
        elasticsearchRestTemplate.indexOps(IndexCoordinates.of(INDEX_NAME)).create();
        elasticsearchRestTemplate.indexOps(IndexCoordinates.of(INDEX_NAME)).refresh();
    }

    @Test
    public void testCreateDocument() {
        // 创建文档内容
        Map<String, Object> document = new HashMap<>();
        document.put("title", "Test Document");
        document.put("content", "This is a test document");

        // 使用 IndexQuery 创建文档
        IndexQuery indexQuery = new IndexQueryBuilder()
                .withId(DOCUMENT_ID)
                .withObject(document)
                .build();

        // 执行创建操作
        String documentId = elasticsearchRestTemplate.index(indexQuery, IndexCoordinates.of(INDEX_NAME));

        // 验证文档是否创建成功
        Assertions.assertEquals(DOCUMENT_ID, documentId);
    }

    @Test
    public void testUpdateDocument() {
        // 首先创建文档
        testCreateDocument();

        // 准备更新的字段
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("content", "This is an updated test document");

        // 构建 UpdateQuery
        UpdateQuery updateQuery = UpdateQuery.builder(DOCUMENT_ID)
                .withDocument(Document.from(updateMap))
                .build();

        // 执行更新操作
        elasticsearchRestTemplate.update(updateQuery, IndexCoordinates.of(INDEX_NAME));

        // 验证更新后的内容
        Map<String, Object> updatedDocument = elasticsearchRestTemplate.get(DOCUMENT_ID, Map.class, IndexCoordinates.of(INDEX_NAME));
        Assertions.assertNotNull(updatedDocument);
        Assertions.assertEquals("This is an updated test document", updatedDocument.get("content"));
    }

    @Test
    public void testDeleteDocument() {
        // 首先创建文档
        testCreateDocument();

        // 执行删除操作
        elasticsearchRestTemplate.delete(DOCUMENT_ID, IndexCoordinates.of(INDEX_NAME));

        // 验证文档是否被删除
        Map<String, Object> deletedDocument = elasticsearchRestTemplate.get(DOCUMENT_ID, Map.class, IndexCoordinates.of(INDEX_NAME));
        Assertions.assertNull(deletedDocument);
    }

    @Test
    public void testReadDocument() {
        // 首先创建文档
        testCreateDocument();

        // 读取文档
        Map<String, Object> retrievedDocument = elasticsearchRestTemplate.get(DOCUMENT_ID, Map.class, IndexCoordinates.of(INDEX_NAME));

        // 验证文档内容
        Assertions.assertNotNull(retrievedDocument);
        Assertions.assertEquals("Test Document", retrievedDocument.get("title"));
        Assertions.assertEquals("This is a test document", retrievedDocument.get("content"));
    }

    @Resource
    private QuestionEsDao questionEsDao;

    @Test
    public void test() {
        questionEsDao.findByUserId(1L);
    }
}
