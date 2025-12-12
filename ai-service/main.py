from fastapi import FastAPI, HTTPException, UploadFile, File
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
from transformers import pipeline
from PIL import Image
import io
import uvicorn

app = FastAPI()

print("⏳ Đang tải các model AI chuyên biệt cho Tiếng Việt...")

# 1. Model Embedding Tiếng Việt (Dựa trên PhoBERT)
# Model này tạo vector cực tốt cho tiếng Việt, hiểu từ lóng và ngữ pháp VN
embed_model = SentenceTransformer('VoVanPhuc/sup-SimCSE-VietNamese-phobert-base')

# 2. Model Kiểm duyệt Tiếng Việt (Vietnamese Hate Speech Detection)
# Model này trả về các nhãn: LABEL_0 (Clean), LABEL_1 (Offensive), LABEL_2 (Hate)
moderation_pipeline = pipeline("text-classification", model="HuyNgo/vi-hate-speech-classification")

# 3. Model ảnh
# Classify: 'normal' vs 'nsfw'
image_moderation_pipeline = pipeline("image-classification", model="Falconsai/nsfw_image_detection")

print("✅ AI Service (Vietnamese Version) đã sẵn sàng!")


class TextRequest(BaseModel):
    text: str


@app.get("/")
def health_check():
    return {"status": "AI Service is running (Vietnamese Models)"}


@app.post("/embed")
async def create_embedding(request: TextRequest):
    try:
        if not request.text.strip():
            raise HTTPException(status_code=400, detail="Text cannot be empty")

        # Generate vector
        embedding = embed_model.encode(request.text)

        # Convert numpy array to list
        return {"vector": embedding.tolist(), "dimension": len(embedding)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/moderate")
async def moderate_content(request: TextRequest):
    try:
        if not request.text.strip():
            return {"is_toxic": False, "reason": "Empty text"}

        # Cắt ngắn text để tránh lỗi độ dài tối đa của model (thường là 256 hoặc 512 tokens)
        text_to_check = request.text[:512]

        # Chạy model kiểm duyệt
        results = moderation_pipeline(text_to_check)
        # Kết quả trả về dạng: [{'label': 'LABEL_0', 'score': 0.98}]

        result = results[0]
        label = result['label']
        score = result['score']

        # Mapping nhãn của model HuyNgo/vi-hate-speech-classification
        # LABEL_0: Clean (Sạch)
        # LABEL_1: Offensive (Xúc phạm nhẹ/Thô tục)
        # LABEL_2: Hate Speech (Ngôn từ thù địch/Phân biệt vùng miền...)

        is_toxic = False
        reason = "Clean"

        if label == 'LABEL_1':
            is_toxic = True
            reason = "Offensive content (Ngôn từ xúc phạm/Thô tục)"
        elif label == 'LABEL_2':
            is_toxic = True
            reason = "Hate speech (Ngôn từ thù địch)"

        # Nếu độ tin cậy thấp (< 0.6) thì có thể coi là an toàn để tránh block nhầm
        if is_toxic and score < 0.6:
            is_toxic = False
            reason += " (Low confidence, allowed)"

        return {
            "is_toxic": is_toxic,
            "flags": [label] if is_toxic else [],
            "reason": reason,
            "score": score
        }

    except Exception as e:
        print(f"Lỗi Moderation: {e}")
        # Fail-open: Nếu lỗi AI, tạm thời cho qua
        return {"is_toxic": False, "error": str(e)}

@app.post("/moderate/image")
async def moderate_image(file: UploadFile = File(...)):
    try:
        # 1. Đọc file ảnh từ request
        image_data = await file.read()
        image = Image.open(io.BytesIO(image_data))

        # 2. Chạy model dự đoán
        results = image_moderation_pipeline(image)
        # Kết quả dạng: [{'label': 'nsfw', 'score': 0.98}, {'label': 'normal', 'score': 0.02}]

        # 3. Phân tích kết quả
        # Lấy nhãn có điểm cao nhất
        top_result = results[0]
        label = top_result['label']
        score = top_result['score']

        is_toxic = False
        reason = "Clean image"

        if label == 'nsfw' and score > 0.7: # Ngưỡng 70%
            is_toxic = True
            reason = f"Hình ảnh nhạy cảm/NSFW ({round(score*100, 2)}%)"

        return {
            "is_toxic": is_toxic,
            "reason": reason,
            "score": score,
            "label": label
        }

    except Exception as e:
        print(f"Lỗi Image Moderation: {e}")
        return {"is_toxic": False, "error": str(e)}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=5000)