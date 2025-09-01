package com.yourname.couplespace.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.yourname.couplespace.R;

import java.text.SimpleDateFormat;
import java.util.*;
import android.util.Log;

public class MoodFragment extends Fragment {
    private FirebaseFirestore db;
    private FirebaseUser me;
    private String coupleId;
    private String partnerUid;         // uid của người kia
    private List<String> members;      // 2 thành viên của couple
    private RadioGroup emojiGroup;
    private EditText edtNote;
    private ListenerRegistration partnerListener;
    private TextView txtPartnerMood, txtHistory, txtToday;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mood, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        db = FirebaseFirestore.getInstance();
        me = FirebaseAuth.getInstance().getCurrentUser();

        emojiGroup     = v.findViewById(R.id.emojiGroup);
        edtNote        = v.findViewById(R.id.edtNote);
        txtPartnerMood = v.findViewById(R.id.txtPartnerMood);
        txtHistory     = v.findViewById(R.id.txtHistory);
        txtToday       = v.findViewById(R.id.txtToday);

        // hiển thị ngày
        txtToday.setText("Mood hôm nay (" + todayStr("dd/MM/yyyy") + ")");

        v.findViewById(R.id.btnSaveMood).setOnClickListener(x -> saveMood());

        db.collection("users").document(me.getUid()).get().addOnSuccessListener(snap -> {
            coupleId = snap.getString("coupleId");
            if (coupleId != null) {
                fetchMembersThenStart();   // ✅ gọi hàm mới
            } else {
                Toast.makeText(getContext(), "Bạn chưa thuộc cặp nào", Toast.LENGTH_SHORT).show();
            }
        });

    }

    // ===== Helpers =====
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

    // ===== Save mood =====
    private void saveMood() {
        if (coupleId == null) return;

        int checkedId = emojiGroup.getCheckedRadioButtonId();
        if (checkedId == -1) { Toast.makeText(getContext(),"Chọn emoji",Toast.LENGTH_SHORT).show(); return; }

        String emoji = ((RadioButton) emojiGroup.findViewById(checkedId)).getText().toString();
        String note  = edtNote.getText().toString();

        Map<String,Object> entry = new HashMap<>();
        entry.put("emoji", emoji);
        entry.put("note",  note);
        entry.put("ts",    FieldValue.serverTimestamp());

        // ✅ Ghi theo Map lồng: { entries: { uid: entry } } + merge
        Map<String,Object> data = new HashMap<>();
        Map<String,Object> entries = new HashMap<>();
        entries.put(me.getUid(), entry);
        data.put("entries", entries);

        todayDocRef().set(data, SetOptions.merge())
                .addOnSuccessListener(v -> Toast.makeText(getContext(), "Đã lưu mood", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Lỗi: "+e.getMessage(), Toast.LENGTH_SHORT).show());
    }


    // ===== Prefill mood của mình nếu đã có =====
    private void loadMyMood() {
        todayDocRef().get().addOnSuccessListener(snap -> {
            if (!snap.exists()) return;
            Map<String,Object> entries = (Map<String,Object>) snap.get("entries");
            if (entries == null) return;
            Map<String,Object> mine = (Map<String,Object>) entries.get(me.getUid());
            if (mine == null) return;

            String emoji = (String) mine.get("emoji");
            String note  = (String) mine.get("note");
            if (note != null) edtNote.setText(note);

            // chọn lại radio tương ứng
            if ("😊".equals(emoji))      emojiGroup.check(R.id.emojiHappy);
            else if ("😐".equals(emoji)) emojiGroup.check(R.id.emojiNeutral);
            else if ("😢".equals(emoji)) emojiGroup.check(R.id.emojiSad);
            else if ("😠".equals(emoji)) emojiGroup.check(R.id.emojiAngry);
            else if ("🥰".equals(emoji)) emojiGroup.check(R.id.emojiLove);
        });
    }

    // ===== Realtime xem mood của người kia hôm nay =====
    private void watchPartnerMood() {
        if (partnerListener != null) partnerListener.remove();
        partnerListener = todayDocRef().addSnapshotListener((snap, e) -> {
            if (e != null) {
                android.util.Log.e("Mood/Watch", "listen error", e);
                txtPartnerMood.setText("Mood người kia hôm nay: (lỗi quyền/kết nối)");
                return;
            }
            if (snap == null || !snap.exists()) {
                txtPartnerMood.setText("Mood người kia hôm nay: (chưa có)");
                return;
            }

            Map<String,Object> data = snap.getData();
            android.util.Log.d("Mood/Watch", "doc=" + data);

            Map<String,Object> entries = (Map<String,Object>) snap.get("entries");
            if (entries == null || entries.isEmpty()) {
                txtPartnerMood.setText("Mood người kia hôm nay: (chưa có)");
                return;
            }

            // Ưu tiên partnerUid nếu đã biết; nếu chưa, lấy uid đầu tiên khác mình
            String targetUid = partnerUid;
            if (targetUid == null || !entries.containsKey(targetUid)) {
                for (String uid : entries.keySet()) if (!uid.equals(me.getUid())) { targetUid = uid; break; }
            }
            if (targetUid == null || !entries.containsKey(targetUid)) {
                txtPartnerMood.setText("Mood người kia hôm nay: (chưa có)");
                return;
            }

            Map<String,Object> m = (Map<String,Object>) entries.get(targetUid);
            String emoji = m == null ? null : String.valueOf(m.get("emoji"));
            String note  = m == null ? null : String.valueOf(m.get("note"));
            txtPartnerMood.setText("Mood người kia: " + (emoji==null?"…":emoji) + (note==null||"null".equals(note)?"":" – "+note));
        });
    }

    // ===== Lịch sử 7 ngày =====
    private void loadHistory7days() {
        final SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        List<String> ids = new ArrayList<>();
        for (int i=0;i<7;i++) { ids.add(fmt.format(cal.getTime())); cal.add(Calendar.DATE, -1); }

        db.collection("couples").document(coupleId)
                .collection("moods")
                .whereIn(FieldPath.documentId(), ids)
                .get()
                .addOnSuccessListener(snaps -> {
                    android.util.Log.d("Mood/History", "docs=" + snaps.size() + " ids=" + ids);

                    List<DocumentSnapshot> list = new ArrayList<>(snaps.getDocuments());
                    list.sort((a,b) -> b.getId().compareTo(a.getId())); // mới nhất trước

                    StringBuilder sb = new StringBuilder("7 ngày gần nhất:\n");
                    for (DocumentSnapshot d : list) {
                        String date = d.getId(); // yyyyMMdd
                        Map<String,Object> entries = (Map<String,Object>) d.get("entries");

                        String myEmoji = "…";
                        String partnerEmoji = "…";

                        if (entries != null) {
                            Object myObj = entries.get(me.getUid());
                            if (myObj instanceof Map) {
                                Object e1 = ((Map<?,?>) myObj).get("emoji");
                                if (e1 != null) myEmoji = String.valueOf(e1);
                            }
                            String pUid = partnerUid;
                            if (pUid == null) {
                                for (String uid : entries.keySet()) if (!uid.equals(me.getUid())) { pUid = uid; break; }
                            }
                            if (pUid != null) {
                                Object pObj = entries.get(pUid);
                                if (pObj instanceof Map) {
                                    Object e2 = ((Map<?,?>) pObj).get("emoji");
                                    if (e2 != null) partnerEmoji = String.valueOf(e2);
                                }
                            }
                        }

                        sb.append(date).append(": ").append(myEmoji).append(" | ").append(partnerEmoji).append("\n");
                    }
                    txtHistory.setText(sb.toString());
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("Mood/History", "query error", e);
                    txtHistory.setText("Không tải được lịch sử: " + e.getMessage());
                });
    }

    private void fetchMembersThenStart() {
        db.collection("couples").document(coupleId).get()
                .addOnSuccessListener(cpl -> {
                    members = (List<String>) cpl.get("members");
                    if (members == null) members = new ArrayList<>();
                    // partnerUid = uid khác mình (nếu đã có 2 người)
                    partnerUid = null;
                    for (String uid : members) if (!uid.equals(me.getUid())) partnerUid = uid;

                    // Log để biết chắc
                    android.util.Log.d("Mood/FetchMembers",
                            "members=" + members + " partnerUid=" + partnerUid);

                    loadMyMood();
                    watchPartnerMood();
                    loadHistory7days();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),"Lỗi đọc couple: "+e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }


    @Override public void onDestroyView() {
        super.onDestroyView();
        if (partnerListener != null) { partnerListener.remove(); partnerListener = null; }
    }
}
