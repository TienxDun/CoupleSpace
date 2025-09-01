package com.yourname.couplespace.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.yourname.couplespace.R;

import java.text.SimpleDateFormat;
import java.util.*;

public class MoodFragment extends Fragment {

    // Firebase
    private FirebaseFirestore db;
    private FirebaseUser me;

    // State
    private String coupleId;
    private String partnerUid;
    private ListenerRegistration partnerListener;

    // UI
    private RadioGroup emojiGroup;
    private EditText edtNote;
    private TextView txtPartnerMood, txtHistory, txtToday, txtPartnerName, txtMyMoodStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mood, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        db = FirebaseFirestore.getInstance();
        me = FirebaseAuth.getInstance().getCurrentUser();

        // Bind UI
        emojiGroup      = v.findViewById(R.id.emojiGroup);
        edtNote         = v.findViewById(R.id.edtNote);
        txtPartnerMood  = v.findViewById(R.id.txtPartnerMood);
        txtHistory      = v.findViewById(R.id.txtHistory);
        txtToday        = v.findViewById(R.id.txtToday);
        txtPartnerName  = v.findViewById(R.id.txtPartnerName);
        txtMyMoodStatus = v.findViewById(R.id.txtMyMoodStatus);

        // Header ngày
        txtToday.setText("Mood hôm nay (" + todayStr("dd/MM/yyyy") + ")");

        v.findViewById(R.id.btnSaveMood).setOnClickListener(x -> saveMood());

        // Lấy coupleId → fetch member → load/subscribe
        db.collection("users").document(me.getUid()).get()
                .addOnSuccessListener(snap -> {
                    coupleId = snap.getString("coupleId");
                    if (coupleId == null) {
                        toast("Bạn chưa thuộc cặp nào");
                        return;
                    }
                    fetchPartnerThenStart();
                })
                .addOnFailureListener(e -> toast("Lỗi đọc user: " + e.getMessage()));
    }

    // ===== Helpers =====
    private void toast(String s){ Toast.makeText(getContext(), s, Toast.LENGTH_SHORT).show(); }

    private String todayStr(String fmt) {
        return new SimpleDateFormat(fmt, Locale.getDefault()).format(new Date());
    }

    private String todayDocId() { // yyyyMMdd
        return todayStr("yyyyMMdd");
    }

    private DocumentReference todayDocRef() {
        return db.collection("couples").document(coupleId)
                .collection("moods").document(todayDocId());
    }

    // ===== Save mood (merge theo entries.{uid}) =====
    private void saveMood() {
        if (coupleId == null) return;

        int checkedId = emojiGroup.getCheckedRadioButtonId();
        if (checkedId == -1) { toast("Chọn emoji"); return; }

        String emoji = ((RadioButton) emojiGroup.findViewById(checkedId)).getText().toString();
        String note  = edtNote.getText().toString();

        Map<String,Object> entry = new HashMap<>();
        entry.put("emoji", emoji);
        entry.put("note",  note);
        entry.put("ts",    FieldValue.serverTimestamp());

        Map<String,Object> data = new HashMap<>();
        Map<String,Object> entries = new HashMap<>();
        entries.put(me.getUid(), entry);
        data.put("entries", entries);

        todayDocRef().set(data, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    toast("Đã lưu mood");
                    updateMyMoodStatus(emoji, note);
                })
                .addOnFailureListener(e -> toast("Lỗi: " + e.getMessage()));
    }

    private void updateMyMoodStatus(String emoji, String note) {
        String status = "Mood của bạn: " + emoji;
        if (!TextUtils.isEmpty(note)) status += " - " + note.trim();
        txtMyMoodStatus.setText(status);
        txtMyMoodStatus.setVisibility(View.VISIBLE);
    }

    // ===== Điền lại mood của mình nếu đã có =====
    private void loadMyMood() {
        todayDocRef().get().addOnSuccessListener(snap -> {
            if (!snap.exists()) return;
            Map<String,Object> entries = (Map<String,Object>) snap.get("entries");
            if (entries == null) return;
            Map<String,Object> mine = (Map<String,Object>) entries.get(me.getUid());
            if (mine == null) return;

            String emoji = (String) mine.get("emoji");
            String note  = (String) mine.get("note");

            if (!TextUtils.isEmpty(note)) edtNote.setText(note);
            if ("😊".equals(emoji))      emojiGroup.check(R.id.emojiHappy);
            else if ("😐".equals(emoji)) emojiGroup.check(R.id.emojiNeutral);
            else if ("😢".equals(emoji)) emojiGroup.check(R.id.emojiSad);
            else if ("😠".equals(emoji)) emojiGroup.check(R.id.emojiAngry);
            else if ("🥰".equals(emoji)) emojiGroup.check(R.id.emojiLove);

            updateMyMoodStatus(emoji, note);
        });
    }

    // ===== Realtime mood người kia hôm nay =====
    private void watchPartnerMood() {
        if (partnerListener != null) partnerListener.remove();

        partnerListener = todayDocRef().addSnapshotListener((snap, e) -> {
            if (e != null) { txtPartnerMood.setText("Lỗi quyền/kết nối"); return; }
            if (snap == null || !snap.exists()) { txtPartnerMood.setText("Chưa có mood hôm nay"); return; }

            Map<String,Object> entries = (Map<String,Object>) snap.get("entries");
            if (entries == null || entries.isEmpty()) { txtPartnerMood.setText("Chưa có mood hôm nay"); return; }

            // Ưu tiên partnerUid; nếu null thì chọn uid khác mình trong entries
            String targetUid = partnerUid;
            if (TextUtils.isEmpty(targetUid) || !entries.containsKey(targetUid)) {
                for (String uid : entries.keySet()) if (!uid.equals(me.getUid())) { targetUid = uid; break; }
            }
            if (TextUtils.isEmpty(targetUid) || !entries.containsKey(targetUid)) {
                txtPartnerMood.setText("Chưa có mood hôm nay"); return;
            }

            Map<String,Object> m = (Map<String,Object>) entries.get(targetUid);
            String emoji = m == null ? null : String.valueOf(m.get("emoji"));
            String note  = m == null ? null : String.valueOf(m.get("note"));

            String moodText = TextUtils.isEmpty(emoji) ? "😊" : emoji;
            if (!TextUtils.isEmpty(note) && !"null".equals(note)) {
                moodText += "\n" + note.trim();
            }
            txtPartnerMood.setText(moodText);
        });
    }

    // ===== Lịch sử 7 ngày: "dd/MM: my | partner" =====
    private void loadHistory7days() {
        final SimpleDateFormat idFmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        final SimpleDateFormat showFmt = new SimpleDateFormat("dd/MM", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        List<String> ids = new ArrayList<>();
        for (int i=0; i<7; i++) { ids.add(idFmt.format(cal.getTime())); cal.add(Calendar.DATE, -1); }

        db.collection("couples").document(coupleId)
                .collection("moods")
                .whereIn(FieldPath.documentId(), ids)
                .get()
                .addOnSuccessListener(snaps -> {
                    List<DocumentSnapshot> list = new ArrayList<>(snaps.getDocuments());
                    list.sort((a,b) -> b.getId().compareTo(a.getId())); // mới → cũ

                    StringBuilder sb = new StringBuilder();
                    for (DocumentSnapshot d : list) {
                        String id = d.getId(); // yyyyMMdd
                        Map<String,Object> entries = (Map<String,Object>) d.get("entries");

                        String myEmoji = "…";
                        String partnerEmoji = "…";

                        if (entries != null) {
                            Object meObj = entries.get(me.getUid());
                            if (meObj instanceof Map) {
                                Object e1 = ((Map<?,?>) meObj).get("emoji");
                                if (e1 != null) myEmoji = String.valueOf(e1);
                            }

                            String pUid = partnerUid;
                            if (TextUtils.isEmpty(pUid)) {
                                for (String uid : entries.keySet()) if (!uid.equals(me.getUid())) { pUid = uid; break; }
                            }
                            if (!TextUtils.isEmpty(pUid)) {
                                Object pObj = entries.get(pUid);
                                if (pObj instanceof Map) {
                                    Object e2 = ((Map<?,?>) pObj).get("emoji");
                                    if (e2 != null) partnerEmoji = String.valueOf(e2);
                                }
                            }
                        }

                        try {
                            Date date = idFmt.parse(id);
                            sb.append(showFmt.format(date));
                        } catch (Exception ignore) {
                            sb.append(id);
                        }
                        sb.append(": ").append(myEmoji).append(" | ").append(partnerEmoji).append("\n");
                    }
                    txtHistory.setText(sb.toString());
                })
                .addOnFailureListener(e -> txtHistory.setText("Không tải được lịch sử: " + e.getMessage()));
    }

    // ===== Đọc partnerUid + tên rồi khởi động các luồng =====
    private void fetchPartnerThenStart() {
        db.collection("couples").document(coupleId).get()
                .addOnSuccessListener(cpl -> {
                    List<String> members = (List<String>) cpl.get("members");
                    if (members != null) {
                        for (String uid : members) if (!uid.equals(me.getUid())) partnerUid = uid;
                    }

                    if (!TextUtils.isEmpty(partnerUid)) {
                        db.collection("users").document(partnerUid).get()
                                .addOnSuccessListener(u -> {
                                    // ưu tiên displayName; fallback email; cuối cùng "Người yêu"
                                    String name = nvl(u.getString("displayName"),
                                            nvl(u.getString("name"),
                                                    nvl(u.getString("email"), "Người yêu")));
                                    txtPartnerName.setText("Mood của " + name);
                                });
                    }

                    loadMyMood();
                    watchPartnerMood();
                    loadHistory7days();
                })
                .addOnFailureListener(e -> toast("Lỗi đọc couple: " + e.getMessage()));
    }

    private static String nvl(String s, String alt) { return TextUtils.isEmpty(s) ? alt : s; }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (partnerListener != null) { partnerListener.remove(); partnerListener = null; }
    }
}
