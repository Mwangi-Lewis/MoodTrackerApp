import io
from typing import Optional

from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

import numpy as np
import cv2
from fer import FER
import uvicorn
from pydantic import BaseModel

# ---------- FastAPI app ----------
app = FastAPI(title="Mood FER + Student Chat Backend")

# Allow your phone / emulator to call this API
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],     # later you can restrict to your phone's IP / domain
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------- Load FER detector once ----------
# mtcnn=True is more accurate but heavier; set False if it feels too slow
detector = FER(mtcnn=True)

# ---------- Pydantic models for chat ----------
class ChatRequest(BaseModel):
    mood: str
    message: Optional[str] = None   # what the student typed


class ChatResponse(BaseModel):
    reply: str


@app.get("/health")
async def health():
    """Simple health-check endpoint."""
    return {"status": "ok"}


# ---------- Emotion detection endpoint ----------

@app.post("/detect")
async def detect_emotion(file: UploadFile = File(...)):
    """
    Accepts one image (JPEG/PNG) and returns the dominant emotion.
    """
    try:
        contents = await file.read()
        if not contents:
            raise HTTPException(status_code=400, detail="Empty file")

        # Convert bytes -> OpenCV image
        npimg = np.frombuffer(contents, np.uint8)
        frame = cv2.imdecode(npimg, cv2.IMREAD_COLOR)
        if frame is None:
            raise HTTPException(status_code=400, detail="Cannot decode image")

        # Run FER
        detections = detector.detect_emotions(frame)

        if not detections:
            # No face detected
            return JSONResponse(
                {
                    "emotion": None,
                    "score": 0.0,
                    "message": "No face detected",
                    "detections": [],
                }
            )

        # Pick the largest face (in case there are many)
        best = max(detections, key=lambda d: d["box"][2] * d["box"][3])
        box = best["box"]              # [x, y, w, h]
        emotions = best["emotions"]    # dict: {"happy": 0.8, ...}

        # Dominant emotion
        emotion, score = max(emotions.items(), key=lambda kv: kv[1])

        return JSONResponse(
            {
                "emotion": emotion,
                "score": float(score),
                "box": box,
                "emotions": emotions,
            }
        )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Server error: {e}")


# ---------- Helper: detect context from typed text ----------

def detect_context(text: str) -> Optional[str]:
    """
    Very simple keyword-based classifier for student situations.
    Returns one of:
      "exam_stress", "assignment_stress", "deadline",
      "lonely", "burnout", "sleep", "family",
      "social_anxiety", "money", "motivation", or None
    """
    t = text.lower()

    # Exam / test stress
    if any(k in t for k in ["exam", "exams", "test", "tests", "midterm", "final", "supp", "cat"]):
        return "exam_stress"

    # Assignment / project / deadline stress
    if any(k in t for k in ["assignment", "assignments", "project", "projects", "report", "lab"]):
        return "assignment_stress"

    if any(k in t for k in ["deadline", "due date", "submission", "submit"]):
        return "deadline"

    # Loneliness / social isolation
    if any(k in t for k in ["lonely", "alone", "no friends", "left out", "isolated", "nobody"]):
        return "lonely"

    # Burnout / overwhelmed
    if any(k in t for k in ["burnout", "burned out", "overwhelmed", "can't cope", "too much"]):
        return "burnout"

    # Sleep / tiredness
    if any(k in t for k in ["tired", "exhausted", "no sleep", "insomnia", "can't sleep", "sleep"]):
        return "sleep"

    # Family related
    if any(k in t for k in ["family", "parents", "mum", "mom", "dad", "home"]):
        return "family"

    # Social anxiety / people stress
    if any(k in t for k in ["people", "crowd", "social", "present", "presentation", "talk in class", "shy"]):
        return "social_anxiety"

    # Money stress
    if any(k in t for k in ["money", "fees", "rent", "broke", "bursary", "scholarship"]):
        return "money"

    # Motivation / “I don’t care”
    if any(k in t for k in ["motivation", "motivated", "don't care", "dont care", "cannot focus", "no energy"]):
        return "motivation"

    return None


# ---------- Student-focused mood-aware chatbot endpoint ----------

@app.post("/chat", response_model=ChatResponse)
async def chat(body: ChatRequest):
    """
    Rule-based helper for university students.
    It uses:
      - mood: "happy", "sad", "angry", "tired", etc.
      - message: student's note (optional)
    to return a gentle, supportive reply.
    """
    mood = (body.mood or "neutral").lower()
    user_text_raw = (body.message or "").strip()
    user_text = user_text_raw.lower()

    context = detect_context(user_text)

    # ---------- Start with a base message depending on mood ----------
    if mood in ["happy", "joy", "amazing", "good"]:
        reply = (
            "I'm really glad you're feeling good right now. "
            "Take a second to notice what is going well and maybe note it in your mood log, "
            "so you can look back on it during tougher days. "
        )
    elif mood in ["sad", "down", "low", "depressed"]:
        reply = (
            "I'm sorry you're feeling low. It's okay to feel this way, especially with all the pressure "
            "around classes, friends, and family. Try to be kind to yourself right now. "
        )
    elif mood in ["angry", "mad", "frustrated"]:
        reply = (
            "It sounds like you're really upset. Those feelings are valid. "
            "Taking a short break, breathing slowly, or writing down what triggered you "
            "can help you respond more calmly instead of reacting in the moment. "
        )
    elif mood in ["tired", "exhausted", "drained"]:
        reply = (
            "You seem really tired. Balancing lectures, assignments, and life can be draining. "
            "If you can, give yourself permission to rest a bit, drink some water, or step away "
            "from your screen. Small breaks are not a waste of time. "
        )
    elif mood in ["anxious", "nervous", "stressed"]:
        reply = (
            "You sound quite anxious. That's understandable when you have many things on your mind. "
            "Try to slow down your breathing and focus on just one small step you can do next. "
        )
    else:
        reply = (
            "Thanks for checking in. However you're feeling, it matters. "
            "You don't have to fix everything today — small steps and small moments of care "
            "for yourself are good enough for now. "
        )

    # ---------- Adjust the reply depending on the context (student-specific) ----------
    if context == "exam_stress":
        reply += (
            "\n\nIt sounds like exams are stressing you. One thing that can help is breaking revision "
            "into very small tasks (for example: 25 minutes on one topic, then a short break). "
            "You don't have to study everything at once. Making a simple plan for today only "
            "can make it feel less heavy."
        )
    elif context == "assignment_stress":
        reply += (
            "\n\nAssignments can pile up quickly. Try listing your tasks and choosing the easiest "
            "or shortest one to start with. Finishing even a small part can give you momentum. "
            "You could also note in your log which course is stressing you most, so you see patterns."
        )
    elif context == "deadline":
        reply += (
            "\n\nDeadlines can feel scary. If possible, prioritise the assignment with the earliest due date "
            "or the one with the highest weight on your grade. Even 20–30 minutes of focused work today "
            "is progress, and you can log how it made you feel afterwards."
        )
    elif context == "lonely":
        reply += (
            "\n\nFeeling lonely at university is more common than it looks from the outside. "
            "You are not strange for feeling this way. If you have the energy, you could try sending "
            "a simple message to one person you trust, or joining a club or group you feel safe in. "
            "Tiny social steps still count."
        )
    elif context == "burnout":
        reply += (
            "\n\nYou might be close to burnout. When everything feels too much, your mind and body are asking "
            "for rest. See if you can lower the pressure on yourself for a bit: shorter to-do lists, realistic goals, "
            "and breaks that are actually restful (sleep, walks, quiet time)."
        )
    elif context == "sleep":
        reply += (
            "\n\nLack of sleep affects mood, focus, and even how we see ourselves. "
            "If you can, try to protect at least a small sleep routine: going to bed around the same time, "
            "keeping your phone away for a bit before sleep, and avoiding heavy studying in bed."
        )
    elif context == "family":
        reply += (
            "\n\nFamily pressure or worries can be very heavy on top of school. "
            "It's okay to admit that it's a lot. If it feels safe, you could talk to someone you trust at school "
            "about it, or write down what is in your control and what is not."
        )
    elif context == "social_anxiety":
        reply += (
            "\n\nSocial situations like group work or presentations can be stressful. "
            "You might try preparing a small script or practising with a friend. "
            "Remember that most people are focused on themselves more than on judging you."
        )
    elif context == "money":
        reply += (
            "\n\nMoney and fees stress can sit in the back of your mind all day. "
            "You could look into support options at your university (bursaries, counselling, student support). "
            "Even just listing your main money concerns on paper can make them feel less chaotic."
        )
    elif context == "motivation":
        reply += (
            "\n\nFeeling unmotivated does not mean you're lazy. It often comes from tiredness, stress, or feeling stuck. "
            "You might try one tiny action (like opening your notes and reading one page) just to get started. "
            "You can log how your motivation changes over time to see patterns."
        )

    # ---------- Personalise with what they said ----------
    if user_text_raw:
        reply += f"\n\nYou wrote: “{user_text_raw}”. "
        reply += (
            "If you want, you can add more detail in your mood log so you can look back later "
            "and see what tends to make things better or worse."
        )

    # Small gentle disclaimer
    reply += (
        "\n\nThis app can't replace professional help, but it can help you notice patterns and "
        "remind you to take small steps to care for yourself."
    )

    return ChatResponse(reply=reply)


# ---------- Run with: python server.py ----------
if __name__ == "__main__":
    uvicorn.run(
        "server:app",
        host="0.0.0.0",   # so your phone on same Wi-Fi can reach it
        port=8000,
        reload=True
    )
