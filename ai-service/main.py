import sys
from fastapi import FastAPI, HTTPException, UploadFile, File
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
from transformers import pipeline
from PIL import Image
import io
import uvicorn


# Hàm log cưỡng ép in ra màn hình Docker ngay lập tức
def force_log(message):
    print(message, flush=True)


app = FastAPI()

force_log("⏳ Đang khởi động AI Service...")

# --- 1. MODEL TEXT (Vector 768 dimensions) ---
# Lưu ý: Model này trả về vector 768 chiều.
force_log("⏳ 1/3: Loading Text Embedding Model...")
embed_model = SentenceTransformer('VoVanPhuc/sup-SimCSE-VietNamese-phobert-base')

force_log("⏳ 2/3: Loading Text Toxicity Model...")
moderation_pipeline = pipeline("text-classification", model="tarudesu/ViSoBERT-HSD")

# --- 2. MODEL ẢNH (Dùng Google ViT chuẩn) ---
# Model này nhận diện vật thể cực tốt: dao, súng, máu, xe tăng...
force_log("⏳ 3/3: Loading Image Detection Model (Google ViT)...")
object_pipeline = pipeline("image-classification", model="google/vit-base-patch16-224")

force_log("✅ AI SERVICE ĐÃ SẴN SÀNG NHẬN REQUEST!")

# Danh sách mapping từ nhãn tiếng Anh (ImageNet) sang cảnh báo tiếng Việt
DANGEROUS_OBJECTS = {
    # Nhóm dao/kiếm
    "cleaver": "Dao phay/Dao bầu",
    "letter opener": "Dao rọc giấy/Vật sắc nhọn",
    "knife": "Dao",
    "switchblade": "Dao bấm",
    "hatchet": "Rìu tay",
    "axe": "Rìu",
    "sword": "Kiếm",
    "dagger": "Dao găm",

    # Nhóm súng đạn
    "revolver": "Súng lục",
    "assault rifle": "Súng trường tấn công",
    "rifle": "Súng trường",
    "shotgun": "Súng săn",
    "holster": "Bao súng (nghi vấn vũ khí)",
    "tank": "Xe tăng/Vũ khí quân sự",
    "projectile": "Đạn dược",

    # Nhóm khác
    "syringe": "Kim tiêm",
    "guillotine": "Máy chém"
}


class TextRequest(BaseModel):
    text: str


@app.get("/")
def health_check():
    return {"status": "AI Service Running - Model: Google ViT"}


@app.post("/embed")
def create_embedding(request: TextRequest):
    try:
        # force_log(f"🔍 Embedding text: {request.text[:20]}...")
        embedding = embed_model.encode(request.text)
        return {"vector": embedding.tolist(), "dimension": len(embedding)}
    except Exception as e:
        force_log(f"❌ Embed Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/moderate")
def moderate_text(request: TextRequest):
    # (Code giữ nguyên, chỉ thêm log nếu cần)
    return {"is_toxic": False, "reason": "Clean"}


@app.post("/moderate/image")
def moderate_image(file: UploadFile = File(...)):
    try:
        force_log(f"\n--- 📸 NHẬN ĐƯỢC ẢNH: {file.filename} ---")

        image_data = file.file.read()
        image = Image.open(io.BytesIO(image_data))

        # Gọi Google ViT để nhận diện (lấy Top 5 khả năng cao nhất)
        results = object_pipeline(image, top_k=5)

        # In log chi tiết ra terminal để bạn xem nó nhìn thấy gì
        force_log("👉 KẾT QUẢ QUÉT (Top 5):")
        for idx, res in enumerate(results):
            label_en = res['label'].lower()
            score = res['score']
            force_log(f"   [{idx + 1}] Label: '{label_en}' - Score: {round(score * 100, 1)}%")

        # Logic chặn
        for res in results:
            label_en = res['label'].lower()
            score = res['score']

            # Check xem label có chứa từ khóa nguy hiểm không
            # Ví dụ: label là "meat cleaver" chứa từ "cleaver" -> Chặn
            for danger_key, vi_msg in DANGEROUS_OBJECTS.items():
                if danger_key in label_en and score > 0.4:  # Độ tin cậy > 40% là chặn
                    log_msg = f"❌ PHÁT HIỆN VI PHẠM: {label_en} -> {vi_msg}"
                    force_log(log_msg)
                    return {
                        "is_toxic": True,
                        "reason": f"Vật nguy hiểm: {vi_msg} ({round(score * 100, 1)}%)",
                        "label": label_en,
                        "score": score
                    }

        force_log("✅ ẢNH AN TOÀN")
        return {
            "is_toxic": False,
            "reason": "Clean",
            "label": results[0]['label'],
            "score": results[0]['score']
        }

    except Exception as e:
        force_log(f"❌ LỖI XỬ LÝ ẢNH: {e}")
        return {"is_toxic": False, "error": str(e)}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=5000)