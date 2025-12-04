package com.kt.social.infra.milvus;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.QueryResults;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.FieldDataWrapper;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class MilvusService {

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private int port;

    @Value("${milvus.vector-dim}")
    private int vectorDim; // 384

    private MilvusServiceClient milvusClient;
    private static final String POST_COLLECTION = "post_collection";

    @PostConstruct
    public void init() {
        // 1. Kết nối Milvus
        milvusClient = new MilvusServiceClient(
                ConnectParam.newBuilder()
                        .withHost(host)
                        .withPort(port)
                        .build()
        );
        log.info("✅ Connected to Milvus");

        // 2. Tạo Collection nếu chưa có
        createPostCollectionIfNotExists();
    }

    private void createPostCollectionIfNotExists() {
        R<Boolean> hasCollection = milvusClient.hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(POST_COLLECTION).build()
        );

        if (Boolean.FALSE.equals(hasCollection.getData())) {
            log.info("Creating Milvus collection: {}", POST_COLLECTION);

            // Định nghĩa các trường (Schema)
            FieldType idField = FieldType.newBuilder()
                    .withName("post_id")
                    .withDataType(DataType.Int64)
                    .withPrimaryKey(true)
                    .withAutoID(false) // Chúng ta sẽ dùng ID của Postgres
                    .build();

            FieldType vectorField = FieldType.newBuilder()
                    .withName("embedding")
                    .withDataType(DataType.FloatVector)
                    .withDimension(vectorDim)
                    .build();

            // Có thể thêm author_id nếu muốn lọc
            FieldType authorField = FieldType.newBuilder()
                    .withName("author_id")
                    .withDataType(DataType.Int64)
                    .build();

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(POST_COLLECTION)
                    .addFieldType(idField)
                    .addFieldType(authorField)
                    .addFieldType(vectorField)
                    .build();

            milvusClient.createCollection(createParam);

            // Tạo Index (Để tìm kiếm nhanh)
            milvusClient.createIndex(
                    CreateIndexParam.newBuilder()
                            .withCollectionName(POST_COLLECTION)
                            .withFieldName("embedding")
                            .withIndexType(IndexType.IVF_FLAT) // Hoặc HNSW (nhanh hơn nhưng tốn RAM)
                            .withMetricType(MetricType.COSINE) // Dùng Cosine Similarity để so sánh
                            .withExtraParam("{\"nlist\":1024}")
                            .build()
            );
            log.info("✅ Milvus Collection Created & Indexed");
        }
    }

    /**
     * Lưu Vector vào Milvus
     */
    public void savePostVector(Long postId, Long authorId, List<Float> vector) {
        if (vector == null || vector.isEmpty()) return;

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("post_id", Collections.singletonList(postId)));
        fields.add(new InsertParam.Field("author_id", Collections.singletonList(authorId)));
        fields.add(new InsertParam.Field("embedding", Collections.singletonList(vector)));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(POST_COLLECTION)
                .withFields(fields)
                .build();

        milvusClient.insert(insertParam);
        log.info("Saved vector for Post ID: {}", postId);
    }

    /**
     * Tìm kiếm bài viết tương đồng (Semantic Search)
     */
    public List<Long> searchSimilarPosts(List<Float> searchVector, int topK) {
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(POST_COLLECTION)
                .withMetricType(MetricType.COSINE)
                .withTopK(topK)
                .withVectors(Collections.singletonList(searchVector))
                .withVectorFieldName("embedding")
                .withParams("{\"nprobe\":10}")
                .build();

        R<SearchResults> response = milvusClient.search(searchParam);

        if (response.getStatus() != R.Status.Success.getCode()) {
            log.error("Milvus Search Failed: {}", response.getMessage());
            return Collections.emptyList();
        }

        // Lấy dữ liệu thô (SearchResults) -> getResults() -> đưa vào Wrapper
        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());

        List<Long> postIds = new ArrayList<>();

        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
        for (SearchResultsWrapper.IDScore score : scores) {
            postIds.add(score.getLongID());
        }
        return postIds;
    }

    public List<List<Float>> getVectorsByPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return Collections.emptyList();

        // 1. Xây dựng biểu thức lọc: "post_id in [101, 102, 103]"
        String expr = "post_id in " + postIds.toString();

        QueryParam queryParam = QueryParam.newBuilder()
                .withCollectionName(POST_COLLECTION)
                .withExpr(expr)
                .addOutField("embedding") // Quan trọng: Yêu cầu trả về trường vector
                .build();

        // 2. Gọi Milvus Query
        R<QueryResults> response = milvusClient.query(queryParam);

        if (response.getStatus() != R.Status.Success.getCode()) {
            log.error("Milvus Query Failed: {}", response.getMessage());
            return Collections.emptyList();
        }

        // 3. Parse kết quả
        QueryResultsWrapper wrapper = new QueryResultsWrapper(response.getData());
        FieldDataWrapper fieldData = wrapper.getFieldWrapper("embedding");

        // Trả về danh sách các vector (List<List<Float>>)
        return (List<List<Float>>) fieldData.getFieldData();
    }
}