package com.yourname.couplespace.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.yourname.couplespace.R;

import java.util.*;

public class DailyQFragment extends Fragment {

    // Firebase
    private FirebaseFirestore db;
    private FirebaseUser me;

    // State
    private String coupleId, partnerUid;
    private ListenerRegistration todayListener;

    // UI (chỉ giữ những gì thực sự dùng)
    private TextView txtQuestion, txtPartnerAnswer, txtMyAnswerStatus, txtHistory;
    private EditText edtAnswer;
    private ProgressBar progressBar;
    private CardView cardQuestion, cardAnswer, cardPartnerAnswer, cardHistory;
    private ImageView iconMyAnswer, iconPartnerAnswer;

    // Ngân hàng câu hỏi local
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_dailyq, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        db = FirebaseFirestore.getInstance();
        me = FirebaseAuth.getInstance().getCurrentUser();

        // Bind view tối thiểu
        txtQuestion       = v.findViewById(R.id.txtQuestion);
        txtPartnerAnswer  = v.findViewById(R.id.txtPartnerAnswer);
        txtMyAnswerStatus = v.findViewById(R.id.txtMyAnswerStatus);
        edtAnswer         = v.findViewById(R.id.edtAnswer);
        txtHistory        = v.findViewById(R.id.txtHistory);

        progressBar        = v.findViewById(R.id.progressBar);
        cardQuestion       = v.findViewById(R.id.cardQuestion);
        cardAnswer         = v.findViewById(R.id.cardAnswer);
        cardPartnerAnswer  = v.findViewById(R.id.cardPartnerAnswer);
        cardHistory        = v.findViewById(R.id.cardHistory);
        iconMyAnswer       = v.findViewById(R.id.iconMyAnswer);
        iconPartnerAnswer  = v.findViewById(R.id.iconPartnerAnswer);

        v.findViewById(R.id.btnSaveAnswer).setOnClickListener(x -> saveAnswer());
        v.findViewById(R.id.btnReroll).setOnClickListener(x -> rerollQuestionNow());

        showLoadingState();

        // Lấy coupleId → xác định partner → khởi chạy
        db.collection("users").document(me.getUid()).get()
                .addOnSuccessListener(u -> {
                    coupleId = u.getString("coupleId");
                    if (TextUtils.isEmpty(coupleId)) {
                        showErrorState("Bạn chưa thuộc cặp nào");
                        return;
                    }
                    fetchPartnerThenStart();
                })
                .addOnFailureListener(e -> showErrorState("Lỗi đọc user: " + e.getMessage()));
    }

    private void showLoadingState() {
        progressBar.setVisibility(View.VISIBLE);
        cardQuestion.setVisibility(View.GONE);
        cardAnswer.setVisibility(View.GONE);
        cardPartnerAnswer.setVisibility(View.GONE);
        cardHistory.setVisibility(View.GONE);
    }

    private void showContent() {
        progressBar.setVisibility(View.GONE);
        cardQuestion.setVisibility(View.VISIBLE);
        cardAnswer.setVisibility(View.VISIBLE);
        cardPartnerAnswer.setVisibility(View.VISIBLE);
        cardHistory.setVisibility(View.VISIBLE);
    }

    private void showErrorState(String message) {
        progressBar.setVisibility(View.GONE);
        toast(message);
        txtQuestion.setText(message);
        cardQuestion.setVisibility(View.VISIBLE);
    }

    // ===== Helpers =====
    private void toast(String s) { Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show(); }

    private String todayId() {
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault());
        f.setTimeZone(tz);
        return f.format(new java.util.Date());
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
    private String pickQuestionWithReroll(int rerollCount) {
        String key = coupleId + "_" + todayId() + "_" + rerollCount;
        int idx = Math.abs(key.hashCode()) % BANK.length;
        return BANK[idx];
    }

    // ===== Flow chính =====
    private void fetchPartnerThenStart() {
        db.collection("couples").document(coupleId).get()
                .addOnSuccessListener(cpl -> {
                    List<String> members = (List<String>) cpl.get("members");
                    if (members != null) {
                        for (String uid : members) if (!uid.equals(me.getUid())) partnerUid = uid;
                    }
                    ensureTodayQuestionThenListen();
                    preloadMyAnswerIfAny();
                    loadHistory7days();
                    showContent();
                })
                .addOnFailureListener(e -> showErrorState("Lỗi đọc couple: " + e.getMessage()));
    }

    private void ensureTodayQuestionThenListen() {
        todayDoc().get().addOnSuccessListener(snap -> {
            if (!snap.exists() || TextUtils.isEmpty(snap.getString("question"))) {
                Map<String,Object> data = new HashMap<>();
                data.put("question", pickQuestionDeterministic());
                data.put("createdAt", FieldValue.serverTimestamp());
                todayDoc().set(data, SetOptions.merge());
            }
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
                updatePartnerAnswerStatus(false);
                return;
            }

            // chọn đáp án của partner (nếu biết partnerUid, dùng luôn)
            String targetUid = partnerUid;
            if (TextUtils.isEmpty(targetUid)) {
                for (String uid : answers.keySet())
                    if (!uid.equals(me.getUid())) { targetUid = uid; break; }
            }

            if (!TextUtils.isEmpty(targetUid) && answers.get(targetUid) instanceof Map) {
                Object text = ((Map<?,?>)answers.get(targetUid)).get("text");
                boolean hasAnswer = text != null && !String.valueOf(text).trim().isEmpty();
                txtPartnerAnswer.setText(hasAnswer ? String.valueOf(text) : "Người kia chưa trả lời.");
                updatePartnerAnswerStatus(hasAnswer);
            } else {
                txtPartnerAnswer.setText("Người kia chưa trả lời.");
                updatePartnerAnswerStatus(false);
            }
        });
    }

    private void updatePartnerAnswerStatus(boolean hasAnswer) {
        if (hasAnswer) {
            iconPartnerAnswer.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
            cardPartnerAnswer.setCardBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light));
        } else {
            iconPartnerAnswer.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
            cardPartnerAnswer.setCardBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.white));
        }
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
                    txtMyAnswerStatus.setText("✅ Đã lưu câu trả lời");
                    txtMyAnswerStatus.setVisibility(View.VISIBLE);
                    updateMyAnswerStatus(true);
                }
            }
        });
    }

    private void updateMyAnswerStatus(boolean hasAnswer) {
        if (hasAnswer) {
            iconMyAnswer.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark));
            cardAnswer.setCardBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_light));
        } else {
            iconMyAnswer.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
            cardAnswer.setCardBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.white));
        }
    }

    private void saveAnswer() {
        String ans = edtAnswer.getText().toString().trim();
        if (TextUtils.isEmpty(ans)) { toast("Nhập câu trả lời"); return; }

        Button btnSave = requireView().findViewById(R.id.btnSaveAnswer);
        btnSave.setEnabled(false);
        btnSave.setText("Đang lưu...");

        Map<String,Object> entry = new HashMap<>();
        entry.put("text", ans);
        entry.put("ts", FieldValue.serverTimestamp());

        Map<String,Object> data = new HashMap<>();
        Map<String,Object> answers = new HashMap<>();
        answers.put(me.getUid(), entry);
        data.put("answers", answers);

        todayDoc().set(data, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    toast("✅ Đã lưu thành công!");
                    txtMyAnswerStatus.setText("✅ Đã lưu câu trả lời");
                    txtMyAnswerStatus.setVisibility(View.VISIBLE);
                    updateMyAnswerStatus(true);
                    btnSave.setEnabled(true);
                    btnSave.setText("💾 Cập nhật câu trả lời");
                })
                .addOnFailureListener(e -> {
                    toast("❌ Lỗi: " + e.getMessage());
                    btnSave.setEnabled(true);
                    btnSave.setText("💾 Lưu câu trả lời");
                });
    }

    private void rerollQuestionNow() {
        final Button btn = requireView().findViewById(R.id.btnReroll);
        btn.setEnabled(false);
        btn.setText("Đang đổi...");

        final DocumentReference doc = todayDoc();

        db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(doc);
            Long count = snap.getLong("rerollCount");
            int rerollCount = (count == null) ? 0 : count.intValue();
            rerollCount++;

            String newQ = pickQuestionWithReroll(rerollCount);

            Map<String,Object> updates = new HashMap<>();
            updates.put("rerollCount", rerollCount);
            updates.put("question", newQ);
            updates.put("createdAt", FieldValue.serverTimestamp());
            updates.put("answers", new HashMap<String,Object>()); // reset answers

            tr.set(doc, updates, SetOptions.merge());
            return null;
        }).addOnSuccessListener(v -> {
            toast("✅ Đã đổi câu hỏi!");
            edtAnswer.setText("");
            txtMyAnswerStatus.setText("");
            txtMyAnswerStatus.setVisibility(View.GONE);
            updateMyAnswerStatus(false);
            btn.setEnabled(true);
            btn.setText("🔄 Đổi câu hỏi hôm nay");
        }).addOnFailureListener(e -> {
            toast("❌ Lỗi đổi câu hỏi: " + e.getMessage());
            btn.setEnabled(true);
            btn.setText("🔄 Đổi câu hỏi hôm nay");
        });
    }

    private void loadHistory7days() {
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
        java.text.SimpleDateFormat idFmt = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault());
        idFmt.setTimeZone(tz);
        java.text.SimpleDateFormat showFmt = new java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault());
        showFmt.setTimeZone(tz);

        java.util.Calendar cal = java.util.Calendar.getInstance(tz);
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            ids.add(idFmt.format(cal.getTime()));
            cal.add(java.util.Calendar.DATE, -1);
        }

        db.collection("couples").document(coupleId)
                .collection("dailyQ")
                .whereIn(FieldPath.documentId(), ids)
                .get()
                .addOnSuccessListener(snaps -> {
                    java.util.List<DocumentSnapshot> list = new java.util.ArrayList<>(snaps.getDocuments());
                    list.sort((a,b) -> b.getId().compareTo(a.getId())); // mới → cũ

                    StringBuilder sb = new StringBuilder();
                    for (DocumentSnapshot d : list) {
                        String id = d.getId();
                        Map<String,Object> answers = (Map<String,Object>) d.get("answers");

                        String mine = "…", partner = "…";
                        String mineStatus = "⭕", partnerStatus = "⭕";

                        if (answers != null) {
                            Object meAns = answers.get(me.getUid());
                            if (meAns instanceof Map) {
                                Object txt = ((Map<?,?>) meAns).get("text");
                                if (txt != null && !String.valueOf(txt).trim().isEmpty()) {
                                    mine = shorten(String.valueOf(txt));
                                    mineStatus = "✅";
                                }
                            }
                            String pUid = partnerUid;
                            if (TextUtils.isEmpty(pUid)) {
                                for (String uid : answers.keySet())
                                    if (!uid.equals(me.getUid())) { pUid = uid; break; }
                            }
                            if (!TextUtils.isEmpty(pUid)) {
                                Object pAns = answers.get(pUid);
                                if (pAns instanceof Map) {
                                    Object txt = ((Map<?,?>) pAns).get("text");
                                    if (txt != null && !String.valueOf(txt).trim().isEmpty()) {
                                        partner = shorten(String.valueOf(txt));
                                        partnerStatus = "✅";
                                    }
                                }
                            }
                        }

                        try {
                            java.util.Date date = idFmt.parse(id);
                            sb.append("📅 ").append(showFmt.format(date)).append("\n");
                        } catch (Exception ignore) {
                            sb.append("📅 ").append(id).append("\n");
                        }
                        sb.append(mineStatus).append(" Bạn: ").append(mine).append("\n");
                        sb.append(partnerStatus).append(" Người kia: ").append(partner).append("\n\n");
                    }
                    txtHistory.setText(sb.toString());
                })
                .addOnFailureListener(e -> txtHistory.setText("❌ Không tải được lịch sử: " + e.getMessage()));
    }

    private String shorten(String s) {
        s = s.trim();
        return s.length() <= 30 ? s : s.substring(0, 27) + "...";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (todayListener != null) { todayListener.remove(); todayListener = null; }
    }
}
