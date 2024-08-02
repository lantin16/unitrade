package com.lantin.unitrade;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lantin.unitrade.domain.dto.ItemDoc;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ElasticSearchTest {

    private RestHighLevelClient client;

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

    @Test
    void testMatchAll() throws IOException {
        // 1. 创建request对象
        SearchRequest request = new SearchRequest("items");
        // 2. 配置request参数
        request.source()
                .query(QueryBuilders.matchAllQuery());
        // 3. 发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        // 4. 解析结果
        parseResponse(response);
    }

    /**
     * 解析响应结果
     * @param response
     */
    private void parseResponse(SearchResponse response) {
        SearchHits searchHits = response.getHits();
        // 4.1 总条数
        long total = searchHits.getTotalHits().value;
        System.out.println("total = " + total);
        // 4.2 命中的数据
        SearchHit[] hits = searchHits.getHits();
        for (SearchHit hit : hits) {
            // 4.2.1 获取source结果（source是原始json文档，是不带高亮的）
            String json = hit.getSourceAsString();
            // 4.2.2 转为ItemDoc
            ItemDoc itemDoc = JSONUtil.toBean(json, ItemDoc.class);
            System.out.println("itemDoc = " + itemDoc);

            // 5. 处理高亮结果
            Map<String, HighlightField> hfs = hit.getHighlightFields();
            if (CollUtil.isNotEmpty(hfs)) { // 如果有高亮才解析高亮
                // 5.1 根据高亮字段名获取高亮结果
                HighlightField hf = hfs.get("name");
                // 5.2 获取高亮结果，并覆盖非高亮结果
                String[] strFragments = Arrays.stream(hf.getFragments())
                        .map(Text::string)  // 将Text转为String
                        .toArray(String[]::new);
                String hfName = StrUtil.join("", strFragments); // 如果getFragments()数组有多个元素是需要拼接的
                itemDoc.setName(hfName);    // 这样返回给前端的itemDoc就是name带有高亮标签的了
                System.out.println("hfName = " + hfName);
            }
        }
    }


    @Test
    void testSearch() throws IOException {
        SearchRequest request = new SearchRequest("items");
        request.source()
                .query(QueryBuilders.boolQuery()
                        .must(QueryBuilders.matchQuery("name", "脱脂牛奶"))
                        .filter(QueryBuilders.termQuery("brand", "德亚"))
                        .filter(QueryBuilders.rangeQuery("price").lt(30000))
                );
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parseResponse(response);
    }


    @Test
    void testSortAndPage() throws IOException {
        // 模拟前端传来的分页参数
        int pageNo = 1, pageSize = 5;

        // 1. 创建request对象
        SearchRequest request = new SearchRequest("items");
        // 2. 组织DSL参数
        // 2.1 query条件
        request.source().query(QueryBuilders.matchAllQuery());
        // 2.2 分页
        request.source().from((pageNo - 1) * pageSize).size(pageSize);
        // 2.3 排序
        request.source()
                .sort("sold", SortOrder.DESC)
                .sort("price", SortOrder.ASC);  // 多个排序字段，当销量一样时再按照价格升序排
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4. 解析结果
        parseResponse(response);
    }


    @Test
    void testHighLight() throws IOException {
        // 1. 创建request对象
        SearchRequest request = new SearchRequest("items");
        // 2. 组织DSL参数
        // 2.1 query条件
        request.source().query(QueryBuilders.matchQuery("name", "脱脂牛奶"));
        // 2.2 高亮条件
        request.source().highlighter(SearchSourceBuilder.highlight().field("name"));
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4. 解析结果
        parseResponse(response);
    }


    @Test
    void testAgg() throws IOException {
        // 1. 创建request对象
        SearchRequest request = new SearchRequest("items");
        // 2. 组织DSL参数
        // 2.1 设置size为0
        request.source().size(0);   // 让返回只包含聚合结果，不包含文档
        // 2.2 聚合条件
        String brandAggName = "brandAgg";
        request.source().aggregation(
                AggregationBuilders.terms(brandAggName) // 聚合类型、聚合名称
                        .field("brand") // 聚合字段
                        .size(10));
        // 3. 发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4. 解析聚合结果
        Aggregations aggregations = response.getAggregations();
        // 4.1 根据聚合名称获取对应的聚合
        Terms brandTerms = aggregations.get(brandAggName);  // 上面用的什么聚合类型，这里就用对应的接口来接收，这里不要直接用顶级接口Aggregation接收
        // 4.2 获取buckets
        List<? extends Terms.Bucket> buckets = brandTerms.getBuckets();
        // 4.3 遍历获取每一个bucket
        for (Terms.Bucket bucket : buckets) {
            System.out.println("brand = " + bucket.getKeyAsString());
            System.out.println("count = " + bucket.getDocCount());
        }

    }
}
