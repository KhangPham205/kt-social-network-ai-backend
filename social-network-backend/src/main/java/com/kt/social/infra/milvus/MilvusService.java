package com.kt.social.infra.milvus;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.*;
import io.milvus.param.*;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.FieldDataWrapper;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import io.milvus.param.collection.GetLoadStateParam;
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
    private int vectorDim; // 768

    private MilvusServiceClient milvusClient;
    private static final String POST_COLLECTION = "post_collection";

    @PostConstruct
    public void init() {
        try {
            milvusClient = new MilvusServiceClient(
                    ConnectParam.newBuilder()
                            .withHost(host)
                            .withPort(port)
                            .build()
            );

            createPostCollectionIfNotExists();

            log.info("✅ Connected to Milvus successfully");
        } catch (Exception e) {
            log.warn("KHÔNG THỂ KẾT NỐI MILVUS: {}. Tính năng gợi ý AI sẽ tạm thời bị vô hiệu hóa.", e.getMessage());
        }
    }

    private void createPostCollectionIfNotExists() {

        try {
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

            loadPostCollection();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi khởi tạo Collection: " + e.getMessage());
        }
    }

    /**
     * Lưu Vector vào Milvus
     */
    public void savePostVector(Long postId, Long authorId, List<Float> vector) {
        if (milvusClient == null) {
            log.warn("Milvus client chưa kết nối, bỏ qua lưu vector Post {}", postId);
            return;
        }

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

        if (milvusClient == null) return Collections.emptyList();

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

        if (milvusClient == null || postIds == null || postIds.isEmpty()) return Collections.emptyList();

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

    private void loadPostCollection() {
        // 1. Gửi lệnh Load
        R<RpcStatus> response = milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(POST_COLLECTION)
                        .build()
        );

        if (response.getStatus() != R.Status.Success.getCode()) {
            log.error("❌ Failed to send load command for {}: {}", POST_COLLECTION, response.getMessage());
            return;
        }

        // 2. 🔥 VÒNG LẶP CHỜ (WAIT UNTIL LOADED)
        // Milvus cần vài giây để load vào RAM, ta phải check trạng thái
        log.info("⏳ Waiting for collection {} to be loaded...", POST_COLLECTION);

        int retry = 0;
        while (retry < 10) { // Thử tối đa 10 lần (10 giây)
            R<GetLoadStateResponse> state = milvusClient.getLoadState(
                    GetLoadStateParam.newBuilder()
                            .withCollectionName(POST_COLLECTION)
                            .build()
            );

            if (state.getStatus() == R.Status.Success.getCode() &&
                    state.getData().getState() == LoadState.LoadStateLoaded) {
                log.info("🚀 Collection {} loaded successfully!", POST_COLLECTION);
                return;
            }

            try {
                Thread.sleep(1000); // Chờ 1 giây rồi check lại
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            retry++;
        }

        log.warn("⚠️ Collection load timeout. Search might fail initially.");
    }
}