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
embed_model = SentenceTransformer('VoVanPhuc/sup-SimCSE-VietNamese-phobert-base')

# 2. Model Kiểm duyệt Tiếng Việt (ĐÃ CẬP NHẬT MODEL CHUẨN)
# Model: tarudesu/ViSoBERT-HSD (Fine-tuned trên ViHSD dataset)
moderation_pipeline = pipeline("text-classification", model="tarudesu/ViSoBERT-HSD")

# 3. Model ảnh
image_moderation_pipeline = pipeline("image-classification", model="Falconsai/nsfw_image_detection")

print("✅ AI Service (Vietnamese Version) đã sẵn sàng!")


class TextRequest(BaseModel):
    text: str


@app.get("/")
def health_check():
    return {"status": "AI Service is running (ViSoBERT HSD)"}


@app.post("/embed")
async def create_embedding(request: TextRequest):
    try:
        if not request.text.strip():
            raise HTTPException(status_code=400, detail="Text cannot be empty")

        embedding = embed_model.encode(request.text)
        return {"vector": embedding.tolist(), "dimension": len(embedding)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/moderate")
async def moderate_content(request: TextRequest):
    try:
        if not request.text.strip():
            return {"is_toxic": False, "reason": "Empty text"}

        text_to_check = request.text[:512]

        # Chạy model kiểm duyệt
        results = moderation_pipeline(text_to_check)
        result = results[0]
        label = result['label']  # Có thể là 'LABEL_0', 'LABEL_1', 'CLEAN', 'HATE', v.v.
        score = result['score']

        # --- LOGIC XỬ LÝ NHÃN THÔNG MINH ---
        # Chuẩn ViHSD dataset mapping:
        # 0: CLEAN (Sạch)
        # 1: OFFENSIVE (Xúc phạm)
        # 2: HATE (Thù ghét)

        is_toxic = False
        reason = "Clean"

        # Chuyển label về dạng chữ hoa để so sánh cho chắc chắn
        label_upper = label.upper()

        # Case 1: Label trả về dạng "LABEL_1", "LABEL_2"
        # Case 2: Label trả về dạng text "OFFENSIVE", "HATE"

        if 'LABEL_1' in label_upper or 'OFFENSIVE' in label_upper:
            is_toxic = True
            reason = "Offensive content (Ngôn từ xúc phạm/Thô tục)"

        elif 'LABEL_2' in label_upper or 'HATE' in label_upper:
            is_toxic = True
            reason = "Hate speech (Ngôn từ thù địch)"

        # Ngưỡng an toàn: Nếu máy không chắc chắn (< 60%), hãy bỏ qua để tránh block nhầm người dùng
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
        return {"is_toxic": False, "error": str(e)}


@app.post("/moderate/image")
async def moderate_image(file: UploadFile = File(...)):
    try:
        image_data = await file.read()
        image = Image.open(io.BytesIO(image_data))

        results = image_moderation_pipeline(image)
        top_result = results[0]

        label = top_result['label']
        score = top_result['score']

        is_toxic = False
        reason = "Clean image"

        if label == 'nsfw' and score > 0.7:
            is_toxic = True
            reason = f"Hình ảnh nhạy cảm/NSFW ({round(score * 100, 2)}%)"

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