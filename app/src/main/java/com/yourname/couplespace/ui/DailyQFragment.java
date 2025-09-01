package com.yourname.couplespace.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import com.yourname.couplespace.R;

import java.text.SimpleDateFormat;
import java.util.*;

public class DailyQFragment extends Fragment {

    // Firebase
    private FirebaseFirestore db;
    private FirebaseUser me;

    // State
    private String coupleId, partnerUid;
    private ListenerRegistration todayListener;

    // UI
    private TextView txtQuestion, txtPartnerAnswer, txtMyAnswerStatus, txtTitle;
    private EditText edtAnswer;

    // ======= Bank câu hỏi local (có thể thay bằng Cloud Function sau) =======
    private static final String[] BANK = new String[] {
            "Nếu được đi đâu đó cùng nhau ngay ngày mai, bạn muốn đi đâu?",
            "Điều bạn thích nhất ở người kia là gì?",
            "Bài hát nào khiến bạn nhớ về người kia?",
            "Kỷ niệm đầu tiên giữa hai bạn là gì?",
            "Món ăn muốn cùng nhau thử vào tuần này?",
            "Một thói quen nhỏ của người kia mà bạn thấy dễ thương?",
            "Nếu tặng quà bất ngờ hôm nay, bạn sẽ tặng gì?",
            "Điều khiến bạn cảm thấy được yêu thương là gì?",
            "Một bộ phim bạn muốn xem cùng người kia?",
            "Điều bạn muốn cải thiện trong mối quan hệ?",
            "Ước mơ chung mà hai bạn muốn đạt được?",
            "Thứ 6 tối lý tưởng của bạn là gì?",
            "Một điểm mạnh của người kia mà bạn ngưỡng mộ?",
            "Câu nói nào gần đây làm bạn ấm lòng?",
            "Có điều gì bạn chưa nói nhưng muốn người kia biết?",
            "Nếu cùng học một kỹ năng mới, bạn chọn gì?",
            "Một nơi trong thành phố muốn hẹn hò lại?",
            "Khi căng thẳng, bạn muốn người kia làm gì cho mình?",
            "Món nhà làm bạn muốn cùng nấu?",
            "Nếu viết thư tay cho người kia, tiêu đề sẽ là gì?",
            "Điều gì khiến bạn cảm thấy an toàn khi ở bên người kia?",
            "Kế hoạch nho nhỏ cho cuối tuần này?",
            "Một thử thách cùng nhau trong 7 ngày tới?",
            "Thói quen lành mạnh muốn cùng xây dựng?",
            "Ba từ mô tả mối quan hệ hiện tại?",
            "Một khoảnh khắc gần đây làm bạn mỉm cười?",
            "Nếu đặt tên cho tình yêu này, bạn đặt là gì?",
            "Điều gì bạn muốn cảm ơn người kia hôm nay?",
            "Khi giận nhau, bạn mong cách giải quyết như thế nào?",
            "Một bí mật nhỏ (dễ thương) bạn muốn chia sẻ?"
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dailyq, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        db = FirebaseFirestore.getInstance();
        me = FirebaseAuth.getInstance().getCurrentUser();

        txtTitle         = v.findViewById(R.id.txtTitle);
        txtQuestion      = v.findViewById(R.id.txtQuestion);
        txtPartnerAnswer = v.findViewById(R.id.txtPartnerAnswer);
        txtMyAnswerStatus= v.findViewById(R.id.txtMyAnswerStatus);
        edtAnswer        = v.findViewById(R.id.edtAnswer);
        v.findViewById(R.id.btnSaveAnswer).setOnClickListener(x -> saveAnswer());

        // Lấy coupleId → xác định partner → bắt đầu
        db.collection("users").document(me.getUid()).get()
                .addOnSuccessListener(u -> {
                    coupleId = u.getString("coupleId");
                    if (TextUtils.isEmpty(coupleId)) {
                        toast("Bạn chưa thuộc cặp nào");
                        return;
                    }
                    fetchPartnerThenStart();
                })
                .addOnFailureListener(e -> toast("Lỗi đọc user: " + e.getMessage()));
    }

    // ===== Helpers =====
    private void toast(String s){ Toast.makeText(getContext(), s, Toast.LENGTH_SHORT).show(); }

    private String todayId() {
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
    }
    private DocumentReference todayDoc() {
        return db.collection("couples").document(coupleId)
                .collection("dailyQ").document(todayId());
    }
    private String pickQuestionDeterministic() {
        String key = coupleId + "_" + todayId();
        int idx = Math.abs(key.hashCode()) % BANK.length;
        return BANK[idx];
    }

    // ===== Flow =====
    private void fetchPartnerThenStart() {
        db.collection("couples").document(coupleId).get()
                .addOnSuccessListener(cpl -> {
                    List<String> members = (List<String>) cpl.get("members");
                    if (members != null) {
                        for (String uid : members) if (!uid.equals(me.getUid())) partnerUid = uid;
                    }
                    ensureTodayQuestionThenListen();
                    preloadMyAnswerIfAny(); // điền lại nếu đã trả lời
                })
                .addOnFailureListener(e -> toast("Lỗi đọc couple: " + e.getMessage()));
    }

    private void ensureTodayQuestionThenListen() {
        todayDoc().get().addOnSuccessListener(snap -> {
            if (!snap.exists() || TextUtils.isEmpty(snap.getString("question"))) {
                // Tạo doc hôm nay với question cố định
                Map<String,Object> data = new HashMap<>();
                data.put("question", pickQuestionDeterministic());
                data.put("createdAt", FieldValue.serverTimestamp());
                todayDoc().set(data, SetOptions.merge());
            }
            // Bắt realtime
            watchToday();
        });
    }

    private void watchToday() {
        if (todayListener != null) todayListener.remove();
        todayListener = todayDoc().addSnapshotListener((snap, e) -> {
            if (e != null) { txtQuestion.setText("Lỗi quyền/kết nối"); return; }
            if (snap == null || !snap.exists()) { txtQuestion.setText("Chưa có câu hỏi"); return; }

            String q = snap.getString("question");
            if (TextUtils.isEmpty(q)) q = pickQuestionDeterministic();
            txtQuestion.setText(q);

            Map<String,Object> answers = (Map<String,Object>) snap.get("answers");
            if (answers == null || answers.isEmpty()) {
                txtPartnerAnswer.setText("Người kia chưa trả lời.");
                return;
            }

            // Partner answer
            String targetUid = TextUtils.isEmpty(partnerUid) ? null : partnerUid;
            if (targetUid == null) {
                for (String uid : answers.keySet()) if (!uid.equals(me.getUid())) { targetUid = uid; break; }
            }
            if (targetUid != null && answers.get(targetUid) instanceof Map) {
                Object text = ((Map<?,?>)answers.get(targetUid)).get("text");
                txtPartnerAnswer.setText(TextUtils.isEmpty(String.valueOf(text)) ? "Người kia chưa trả lời." : String.valueOf(text));
            } else {
                txtPartnerAnswer.setText("Người kia chưa trả lời.");
            }
        });
    }

    private void preloadMyAnswerIfAny() {
        todayDoc().get().addOnSuccessListener(snap -> {
            if (!snap.exists()) return;
            Map<String,Object> answers = (Map<String,Object>) snap.get("answers");
            if (answers == null) return;
            Object mine = answers.get(me.getUid());
            if (mine instanceof Map) {
                Object txt = ((Map<?,?>) mine).get("text");
                if (txt != null) {
                    edtAnswer.setText(String.valueOf(txt));
                    txtMyAnswerStatus.setText("Đã lưu câu trả lời");
                    txtMyAnswerStatus.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void saveAnswer() {
        String ans = edtAnswer.getText().toString().trim();
        if (TextUtils.isEmpty(ans)) { toast("Nhập câu trả lời"); return; }

        Map<String,Object> entry = new HashMap<>();
        entry.put("text", ans);
        entry.put("ts", FieldValue.serverTimestamp());

        Map<String,Object> data = new HashMap<>();
        Map<String,Object> answers = new HashMap<>();
        answers.put(me.getUid(), entry);
        data.put("answers", answers);

        todayDoc().set(data, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    toast("Đã lưu");
                    txtMyAnswerStatus.setText("Đã lưu câu trả lời");
                    txtMyAnswerStatus.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> toast("Lỗi: " + e.getMessage()));
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (todayListener != null) { todayListener.remove(); todayListener = null; }
    }
}
