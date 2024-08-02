package com.lantin.unitrade;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantin.unitrade.domain.dto.ItemDoc;
import com.lantin.unitrade.domain.po.Item;
import com.lantin.unitrade.service.IItemService;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

@SpringBootTest(properties = "spring.profiles.active=local")    // 由于要访问数据库，因此需要激活local配置
public class ElasticDocumentTest {

    private RestHighLevelClient client;
    @Autowired
    private IItemService itemService;

    @BeforeEach
    void setUp() {
        client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://192.168.88.129:9200")
        ));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    /**
     * 新增文档与全量更新文档的API一样
     *
     * @throws IOException
     */
    @Test
    void testIndexDoc() throws IOException {
        // 1. 准备文档数据
        // 1.1 从数据库中查出商品信息
        Item item = itemService.getById(317578L);
        // 1.2 将Item转成ItemDoc
        ItemDoc itemDoc = BeanUtil.copyProperties(item, ItemDoc.class);
        // 1.3 将ItemDoc转成json
        String jsonStr = JSONUtil.toJsonStr(itemDoc);

        // 2. 准备request
        IndexRequest request = new IndexRequest("items").id(itemDoc.getId());
        // 3. 准备请求参数
        request.source(jsonStr, XContentType.JSON);
        // 4. 发送请求
        client.index(request, RequestOptions.DEFAULT);  // 文档的操作直接在client中
    }


    @Test
    void testGetDoc() throws IOException {
        // 1. 准备request
        GetRequest request = new GetRequest("items", "317578");
        // 2. 发送请求
        GetResponse response = client.get(request, RequestOptions.DEFAULT); // 这个响应结果包含了文档的完整信息
        // 3. 解析结果，我们通常只需要其中的source属性
        String json = response.getSourceAsString();
        JSONUtil.toBean(json, ItemDoc.class);   // 将json字符串转回ItemDoc对象
        System.out.println("json = " + json);
    }


    @Test
    void testDeleteDoc() throws IOException {
        // 1. 准备request
        DeleteRequest request = new DeleteRequest("items", "317578");
        // 2. 发送请求
        client.delete(request, RequestOptions.DEFAULT);
    }

    @Test
    void testUpdateDoc() throws IOException {
        // 1. 准备request
        UpdateRequest request = new UpdateRequest("items", "317578");
        // 2. 准备请求参数，每两个是一组键值对
        request.doc(
                "price", "25600"
        );
        client.update(request, RequestOptions.DEFAULT);
    }


    /**
     * 将数据库中的商品信息批量添加进es作搜索用
     * @throws IOException
     */
    @Test
    void testBulkDoc() throws IOException {
        int pageNo = 1, pageSize = 500;

        // 循环添加每页商品数据到es索引库
        // 这里不要用list查然后批量添加，因为list是查所有商品数据，一次全查出来可能回爆内存
        while (true) {
            // 1. 准备文档数据
            Page<Item> page = itemService.lambdaQuery()
                    .eq(Item::getStatus, 1)  // 只查上架的商品
                    .page(Page.of(pageNo, pageSize));
            List<Item> records = page.getRecords();
            if (CollUtil.isEmpty(records)) {
                return; // 数据库中的所有商品数据都添加到了es中
            }

            // 2. 准备request
            BulkRequest request = new BulkRequest();

            // 3. 添加要批量提交的请求
            records.stream()
                    .map(item -> BeanUtil.copyProperties(item, ItemDoc.class))
                    .forEach(itemDoc -> {
                        request.add(new IndexRequest("items").id(itemDoc.getId()).source(JSONUtil.toJsonStr(itemDoc),XContentType.JSON));
                    });

            // 4. 发送请求
            client.bulk(request, RequestOptions.DEFAULT);

            // 5. 翻页
            pageNo++;
        }
    }
}
