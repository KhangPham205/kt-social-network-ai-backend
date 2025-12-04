from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
from transformers import pipeline
import uvicorn

app = FastAPI()

# 1. Load model ngôn ngữ (Model này hỗ trợ 50+ ngôn ngữ, bao gồm tiếng Việt)
# Lần đầu chạy sẽ mất thời gian tải model (~1GB)
print("⏳ Đang tải model AI... Vui lòng chờ.")
model = SentenceTransformer('paraphrase-multilingual-MiniLM-L12-v2')
print("✅ Model đã sẵn sàng!")

# 2. Model Kiểm duyệt (Toxic Classification)
# Model này phát hiện: toxic, severe_toxic, obscene, threat, insult, identity_hate
# Nếu muốn chuyên tiếng Việt sâu hơn, hãy đổi thành "uitnlp/visobert" (cần xử lý output khác một chút)
moderation_pipeline = pipeline("text-classification", model="unitary/toxic-bert", top_k=None)

print("✅ AI Service đã sẵn sàng!")

class TextRequest(BaseModel):
    text: str


@app.get("/")
def health_check():
    return {"status": "AI Service is running"}


@app.post("/embed")
async def create_embedding(request: TextRequest):
    try:
        if not request.text.strip():
            raise HTTPException(status_code=400, detail="Text cannot be empty")

        # Generate vector (list of floats)
        embedding = model.encode(request.text)

        # Convert numpy array to list for JSON serialization
        return {"vector": embedding.tolist(), "dimension": len(embedding)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/moderate")
async def moderate_content(request: TextRequest):
    try:
        if not request.text.strip():
            return {"is_toxic": False, "reason": "Empty text"}

        # Giới hạn độ dài text để tránh lỗi model (BERT thường max 512 token)
        text_to_check = request.text[:512]

        # Chạy model
        # Kết quả trả về dạng: [[{'label': 'toxic', 'score': 0.9}, {'label': 'insult', 'score': 0.1}, ...]]
        results = moderation_pipeline(text_to_check)

        # Phân tích kết quả (Lấy danh sách các nhãn có điểm tin cậy > 0.7)
        threshold = 0.7
        flagged_labels = []

        for res in results[0]:  # pipeline trả về list of list
            if res['score'] > threshold:
                # Nếu label là toxic, obscene, threat, insult, identity_hate -> Chặn
                if res['label'] != 'neutral':  # Model toxic-bert không có label neutral, nhưng logic chung là vậy
                    flagged_labels.append(res['label'])

        is_toxic = len(flagged_labels) > 0

        return {
            "is_toxic": is_toxic,
            "flags": flagged_labels,
            "score": results[0][0]['score']  # Điểm của label cao nhất
        }

    except Exception as e:
        print(f"Lỗi Moderation: {e}")
        # Nếu lỗi AI, tạm thời cho qua (Fail-open) hoặc chặn (Fail-closed) tùy chính sách
        return {"is_toxic": False, "error": str(e)}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=5000)