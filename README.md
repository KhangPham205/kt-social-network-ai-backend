# 📌 Social Network Website — Content Moderation & Recommendation System - Backend Repo

**A full-stack social networking platform with AI-powered content moderation and personalized post recommendation.**

---

## 🚀 Overview

This project aims to build a modern social network website that supports multimedia posting, real-time messaging, friend system, content moderation using AI, and personalized post recommendations.

The system is developed for **Đồ án 1 – Trường Đại học Công nghệ Thông tin (UIT – VNUHCM)** by:

* **Phạm Tuấn Khang – 23520707** - Backend Developer
* **Đinh Thanh Tùng – 23521738** - Frontend Developer
* **Instructor:** ThS. Trần Thị Hồng Yến
* **Timeline:** 08/09/2024 → 28/12/2024

---

## 🎯 Project Goals

* Build a complete web-based social media platform
* Provide user-friendly content creation & interaction (post, comment, like, share, etc.)
* Implement real-time chat (1-1 & group)
* Apply AI for:

  * ✅ **Post recommendations** based on user behavior
  * ✅ **Toxic content detection** for automatic moderation
* Provide admin & moderator dashboards
* Optimize user experience, performance and scalability

---

## 📦 Features

### 👤 **User Features**

* Register, login, logout, profile management
* Friend system: send/accept friend requests
* Follow/unfollow system
* Create, edit, delete posts (text, images, videos)
* Like, comment, reply, share posts
* Report posts & users for violations
* Real-time chat (WebSocket/WebRTC):

  * 1-1 messages
  * Group chat
  * Voice/video call
* Search users & posts
* Block/unblock other users
* Receive personalized post recommendations (AI-powered)
* Automatic AI moderation on posts/comments before publishing

---

### 🛡️ **Moderator Features**

* Handle violation reports
* Lock/unlock posts
* Temporarily ban/unban violating users

---

### 👑 **Admin Features**

* User management (create/update/delete roles)
* System configuration (max file size, friend limits, etc.)
* Dashboard analytics (user activity, post metrics, reports…)

---

## 🧠 AI Modules

### 🔍 **1. Content Moderation (Toxicity Detection)**

Uses **Google Perspective API** or **OpenAI Moderation API** to detect harmful content:

* Hate speech
* Harassment
* Explicit content
* Spam

Posts are scanned before saving → violating content is blocked & user receives a warning.

---

### 🌟 **2. Recommendation System**

Hybrid recommendation approach:

* Rule-based scoring (likes, comments, interactions)
* Behavioral tracking
* Content-based similarity (TF-IDF or embeddings)

Goal: Suggest posts aligned with user interests.

---

## 🏗️ System Architecture

### ⚙️ **Backend**

* **Spring Boot (Java)**
* **RESTful APIs**
* **Spring Security + JWT**
* **Spring AI**
* **WebSocket (STOMP)** for real-time messaging
* **PostgreSQL** (main relational DB)
* **Neo4j** (friend graph / relationship-based queries)
* **Cloud storage:** Cloudinary

### 🔧 **Tools**

* Figma (UI/UX)
* DrawSQL / Draw.io
* Postman
* IntelliJ IDEA
* PyCharm
* GitHub

---

## 🗄️ Database Entities (simplified)

* **User**
* **Post**
* **Comment**
* **Reaction**
* **Friendship**
* **Follower**
* **Conversation / Message**
* **Notification**
* **Report**

---

## 📊 Expected Results

* Faster than **2 seconds/page** loading
* Handle **≥ 1000 concurrent users**
* Recommendation accuracy ≥ **80%**
* User satisfaction ≥ **8/10**
* Clean UI, accessible & responsive

---

## 🛠️ Installation & Setup

### Backend

```bash
git clone https://github.com/your-repo/backend.git
cd backend
mvn clean install
```

### Environment Variables

```
DB_URL=
DB_USERNAME=
DB_PASSWORD=

JWT_SECRET=
CLOUDINARY_KEY=
PERSPECTIVE_API_KEY=
```

Run server:

```bash
mvn spring-boot:run
```

---

### Frontend

```bash
git clone https://github.com/your-repo/frontend.git
cd frontend
npm install
npm run dev
```

---

## 🧪 Testing

* Unit tests for backend services
* Postman API testing
* UI tests for core features (login, posting, messaging)

---

## 🔮 Future Improvements

* Native mobile app (React Native / Flutter)
* Advanced recommendation model (ML-based)
* Image/video moderation
* Fake news detection
* Livestream
* Community groups
* Gamification (badges, streaks, ranks)
* Microservices migration

---

## 📜 License

This project is developed for academic purposes under UIT-VNUHCM.

---

## 🧑‍💻 Authors

* [**Phạm Tuấn Khang**](https://github.com/KhangPham205)
* [**Đinh Thanh Tùng**](https://github.com/tungdt312)

## Frontend Repo: [Link](https://github.com/tungdt312/socius-frontend)
---
